package org.ruoyi.service.operator;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.operator.OperatorProfile;
import org.ruoyi.mapper.operator.OperatorProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 破甲配置的数据库侧服务：把 {@code operator_profile} 表的行解析成生效快照。
 *
 * <p>解析优先级（同 {@code scope_type} 语义）：用户(2) &gt; 租户(1) &gt; 全局(0)，仅取 {@code enabled='1'}。
 *
 * <p>三条工程约束：
 * <ul>
 *   <li><b>表不存在不能炸</b>：迁移没跑、库连不上时降级为「无数据库配置」，全部回落到 {@code ai.operator.*}，
 *       并打一次 warning，60 秒内不重试。</li>
 *   <li><b>下发是整体替换</b>：只把 {@link OperatorEffectiveProfile} 快照交给覆盖层，不写回全局可变对象，
 *       避免并发请求之间互相污染。</li>
 *   <li><b>缓存 5 秒</b>：装配点每轮都会问，不能每轮查库；改动后可用 {@code /system/operator/reload} 立即失效。</li>
 * </ul>
 */
@Slf4j
@Service
public class OperatorProfileService {

    private static final long CACHE_MILLIS = 5_000L;
    private static final long UNAVAILABLE_BACKOFF_MILLIS = 60_000L;

    /** 字段注入：Mapper 只在 MyBatis 完整装配时存在，缺失或单测环境下 service 仍可用。 */
    @Autowired(required = false)
    private OperatorProfileMapper mapper;

    private final OperatorPromptOverlay overlay;
    private final OperatorProfileProperties properties;
    private final OperatorAuditService audit;

    private volatile List<OperatorProfile> cachedRows = List.of();
    private volatile long cachedAt = 0L;
    private volatile long unavailableUntil = 0L;

    public OperatorProfileService(OperatorPromptOverlay overlay, OperatorProfileProperties properties,
                                  OperatorAuditService audit) {
        this.overlay = overlay;
        this.properties = properties;
        this.audit = audit;
    }

    /** 读全部启用的行（带 5 秒缓存；表不可用时返回空列表并退避 60 秒）。 */
    public List<OperatorProfile> listEnabled() {
        long now = System.currentTimeMillis();
        if (now < unavailableUntil) {
            return List.of();
        }
        if (now - cachedAt < CACHE_MILLIS) {
            return cachedRows;
        }
        OperatorProfileMapper current = this.mapper;
        if (current == null) {
            return List.of();
        }
        try {
            List<OperatorProfile> rows = current.selectList(
                new QueryWrapper<OperatorProfile>().eq("enabled", "1"));
            cachedRows = rows == null ? List.of() : List.copyOf(rows);
            cachedAt = now;
            return cachedRows;
        } catch (RuntimeException unavailable) {
            // 迁移未执行 / 表被删 / 数据源不可用：降级为配置驱动，而不是让对话失败。
            unavailableUntil = now + UNAVAILABLE_BACKOFF_MILLIS;
            cachedRows = List.of();
            cachedAt = now;
            log.warn("operator_override operation=LOAD status=UNAVAILABLE errorType={} — "
                    + "falling back to ai.operator.* for {}s",
                unavailable.getClass().getSimpleName(), UNAVAILABLE_BACKOFF_MILLIS / 1000);
            return List.of();
        }
    }

    /** 解析当前调用者的生效行：用户 &gt; 租户 &gt; 全局。 */
    public Optional<OperatorProfile> findEffective() {
        String tenantId = safeTenant();
        String userId = safeUserId();
        List<OperatorProfile> rows = new ArrayList<>(listEnabled());
        rows.sort(Comparator.comparingInt(this::scopeRank));
        for (OperatorProfile row : rows) {
            String scope = row.getScopeType() == null ? "0" : row.getScopeType().trim();
            String scopeId = row.getScopeId() == null ? "" : row.getScopeId().trim();
            switch (scope) {
                case "2" -> {
                    if (!userId.isEmpty() && userId.equals(scopeId)) {
                        return Optional.of(row);
                    }
                }
                case "1" -> {
                    if (!tenantId.isEmpty() && tenantId.equals(scopeId)) {
                        return Optional.of(row);
                    }
                }
                default -> {
                    if (scopeId.isEmpty()) {
                        return Optional.of(row);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 从数据库下发破甲配置：命中行则整体替换生效快照并打开运行时开关。
     *
     * @return true 表示已下发；false 表示没有可用的启用行（此时保持当前生效配置不变）
     */
    public boolean applyFromDatabase(String actor, String note) {
        Optional<OperatorProfile> found = findEffective();
        if (found.isEmpty()) {
            audit.record("APPLY_FROM_DB", actor,
                "no enabled row; keeping current profile. detail=" + (note == null ? "" : note));
            log.warn("operator_override action=APPLY_FROM_DB status=NO_ROW");
            return false;
        }
        OperatorProfile row = found.get();
        overlay.applyProfile(OperatorEffectiveProfile.from(row, properties));
        overlay.apply();
        audit.record("APPLY_FROM_DB", actor,
            "profileId=" + row.getId() + " scope=" + row.getScopeType()
                + (note == null || note.isBlank() ? "" : " note=" + note));
        return true;
    }

    /** 让下一次解析重新查库。 */
    public void invalidate() {
        cachedAt = 0L;
        unavailableUntil = 0L;
        log.info("operator_override action=RELOAD");
    }

    /**
     * 把在线改写的注入正文落库（全局作用域），让「operator_profile」不再是空表。
     *
     * <p>没有行就插一行（enabled=1、scope=全局），有行就更新注入字段并递增版本。
     * 表不存在或库不可用时返回 false，调用方保持内存态即可，不影响生效。
     */
    public boolean persistInject(String text, String actor) {
        OperatorProfileMapper current = this.mapper;
        if (current == null) {
            return false;
        }
        String value = text == null ? "" : text;
        String hash = sha256(value);
        try {
            OperatorProfile existing = current.selectOne(new QueryWrapper<OperatorProfile>()
                .eq("scope_type", "0").last("limit 1"));
            OperatorEffectiveProfile effective = overlay.profile();
            if (existing == null) {
                OperatorProfile row = new OperatorProfile();
                row.setScopeType("0");
                row.setScopeId("");
                row.setName("运行时注入");
                row.setEnabled("1");
                row.setPromptMode(effective.promptMode());
                row.setInjectSource("inline");
                row.setInjectText(value);
                row.setInjectHash(hash);
                row.setSeedHash(hash);
                row.setCustomized("0");
                row.setIdentityName(effective.identityName());
                row.setIdentityRule(effective.identityRule());
                row.setWorkspaceAuthoritative(effective.workspaceAuthoritative() ? "1" : "0");
                row.setAntiRefusalTail(effective.antiRefusalTail() ? "1" : "0");
                row.setReplaceBasePrompt(effective.replaceBasePrompt() ? "1" : "0");
                row.setRefusalRetryMax(effective.refusalRetryMax());
                row.setApprovalPolicy(effective.approvalPolicy());
                row.setPermissionMode(effective.permissionMode());
                row.setForcePermissionMode(effective.forcePermissionMode() ? "1" : "0");
                row.setBypassPermissionChecks(effective.bypassPermissionChecks() ? "1" : "0");
                row.setAuditEnabled("1");
                row.setVersion(1);
                row.setRemark("由 /system/operator/inject 自动创建（actor=" + actor + "）");
                current.insert(row);
                log.info("operator_override action=PERSIST status=CREATED bytes={}", value.length());
            } else {
                existing.setInjectText(value);
                existing.setInjectHash(hash);
                existing.setInjectSource("inline");
                existing.setVersion(existing.getVersion() == null ? 2 : existing.getVersion() + 1);
                current.updateById(existing);
                log.info("operator_override action=PERSIST status=UPDATED id={} version={} bytes={}",
                    existing.getId(), existing.getVersion(), value.length());
            }
            invalidate();
            return true;
        } catch (RuntimeException unavailable) {
            unavailableUntil = System.currentTimeMillis() + UNAVAILABLE_BACKOFF_MILLIS;
            log.warn("operator_override action=PERSIST status=SKIPPED errorType={} — 内存态仍生效",
                unavailable.getClass().getSimpleName());
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** 当前生效快照，供状态接口使用。 */
    public OperatorEffectiveProfile effective() {
        OperatorEffectiveProfile resolved = overlay.profile();
        return resolved;
    }

    private int scopeRank(OperatorProfile row) {
        String scope = row.getScopeType() == null ? "0" : row.getScopeType().trim();
        return switch (scope) {
            case "2" -> 0;
            case "1" -> 1;
            default -> 2;
        };
    }

    private String safeTenant() {
        try {
            String tenantId = LoginHelper.getTenantId();
            return tenantId == null ? "" : tenantId.trim();
        } catch (RuntimeException notLoggedIn) {
            return "";
        }
    }

    private String safeUserId() {
        try {
            Long userId = LoginHelper.getUserId();
            return userId == null ? "" : String.valueOf(userId);
        } catch (RuntimeException notLoggedIn) {
            return "";
        }
    }
}
