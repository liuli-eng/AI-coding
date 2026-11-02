package org.ruoyi.service.operator;

import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.operator.OperatorAudit;
import org.ruoyi.mapper.operator.OperatorAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 破甲审计（L5）。
 *
 * <p>对应 Xsdsh-purge 的 {@code applied.json} / {@code override-state.json}：每次生效、回滚、
 * 注入更新都留一条记录。
 *
 * <p>双写：结构化日志（永远可用）+ {@code operator_audit} 表（表不存在或库不可用时静默跳过）。
 * 审计失败绝不能反过来打断主流程，所以这里全部 best-effort。
 */
@Slf4j
@Service
public class OperatorAuditService {

    private static final String AUDIT_PREFIX = "operator_audit";

    private final OperatorPromptOverlay overlay;
    private final OperatorProfileProperties properties;

    /** 可选：表没建或数据源不可用时为 null，此时只落日志。 */
    @Autowired(required = false)
    private OperatorAuditMapper auditMapper;

    public OperatorAuditService(OperatorPromptOverlay overlay, OperatorProfileProperties properties) {
        this.overlay = overlay;
        this.properties = properties;
    }

    /** 记录一次配置动作。 */
    public void record(String action, String actor, String detail) {
        if (!properties.isAuditEnabled()) {
            return;
        }
        String injectHash = overlay.injectHash();
        log.info("{} action={} actor={} enabled={} operatorMode={} promptMode={} identity={} "
                + "injectHash={} approvalPolicy={} permissionMode={} forcePermissionMode={} detail={}",
            AUDIT_PREFIX, action, actor == null ? "anonymous" : actor,
            overlay.enabled(), overlay.operatorMode(), overlay.profile().promptMode(),
            overlay.profile().identityName(), injectHash,
            overlay.profile().approvalPolicy(), overlay.profile().permissionMode(),
            overlay.profile().forcePermissionMode(), detail == null ? "" : detail);
        persist(action, actor, injectHash, detail);
    }

    /** 装配点被调用时的轻量痕迹：只在破甲态记录，避免原生模式刷日志（这一条不写库）。 */
    public void recordInjection(String scene, int headLength, int tailLength) {
        if (!overlay.operatorMode()) {
            return;
        }
        log.info("{} action=INJECT scene={} headChars={} tailChars={} version={}",
            AUDIT_PREFIX, scene, headLength, tailLength, OperatorPromptOverlay.OPERATOR_PROMPT_VERSION);
    }

    private void persist(String action, String actor, String injectHash, String detail) {
        OperatorAuditMapper mapper = this.auditMapper;
        if (mapper == null) {
            return;
        }
        try {
            OperatorAudit row = new OperatorAudit();
            row.setAction(action);
            row.setActor(actor == null ? "anonymous" : truncate(actor, 64));
            row.setInjectHash(injectHash);
            row.setPromptVersion(overlay.operatorMode()
                ? OperatorPromptOverlay.OPERATOR_PROMPT_VERSION : "native");
            row.setDetail(truncate(detail, 1000));
            String tenantId = tenantOrNull();
            if (tenantId != null) {
                row.setTenantId(tenantId);
            }
            mapper.insert(row);
        } catch (RuntimeException unavailable) {
            // 迁移未执行 / 表被删 / 租户上下文缺失：只丢这一条审计，不影响上层。
            log.debug("{} action=PERSIST status=SKIPPED errorType={}",
                AUDIT_PREFIX, unavailable.getClass().getSimpleName());
        }
    }

    private String tenantOrNull() {
        try {
            String tenantId = LoginHelper.getTenantId();
            return tenantId == null || tenantId.isBlank() ? null : tenantId;
        } catch (RuntimeException notLoggedIn) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
