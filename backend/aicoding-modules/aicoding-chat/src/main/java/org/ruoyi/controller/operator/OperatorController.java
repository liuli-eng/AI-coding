package org.ruoyi.controller.operator;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.operator.OperatorProfile;
import org.ruoyi.service.operator.OperatorAuditService;
import org.ruoyi.service.operator.OperatorEffectiveProfile;
import org.ruoyi.service.operator.OperatorProfileProperties;
import org.ruoyi.service.operator.OperatorProfileService;
import org.ruoyi.service.operator.OperatorPromptOverlay;
import org.ruoyi.service.operator.OperatorRefusalStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 破甲（Operator Override）运行态接口。
 *
 * <p>对应 Xsdsh-purge 侧栏「清洗」页的状态与「应用 / 还原」：这里只切换运行时开关并把数据库行下发为
 * 生效快照，不改任何源文件，因此 {@code revert} 是真正的一键回滚，下一轮装配立即生效。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/operator")
public class OperatorController {

    private final OperatorPromptOverlay overlay;
    private final OperatorProfileProperties properties;
    private final OperatorProfileService profileService;
    private final OperatorAuditService audit;
    private final OperatorRefusalStatService refusalStats;

    /** 当前生效状态：开关、模式、身份、注入指纹、Harness 版本、审批与权限默认值。 */
    @GetMapping("/status")
    @SaCheckPermission("system:operator:query")
    public R<Map<String, Object>> status() {
        return R.ok(statusPayload());
    }

    /** 生效：打开运行时开关，刷新注入缓存（以当前生效配置为准）。 */
    @PostMapping("/apply")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> apply(@RequestParam(required = false) String note) {
        overlay.apply();
        audit.record("APPLY", actor(), note);
        return R.ok(statusPayload());
    }

    /** 从数据库下发：命中启用的 profile 行则整体替换生效快照并打开开关。 */
    @PostMapping("/apply-from-db")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> applyFromDatabase(@RequestParam(required = false) String note) {
        boolean applied = profileService.applyFromDatabase(actor(), note);
        Map<String, Object> payload = statusPayload();
        payload.put("appliedFromDatabase", applied);
        return R.ok(payload);
    }

    /** 回滚：关闭运行时开关。提示词、注入文件与历史会话都不受影响。 */
    @PostMapping("/revert")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> revert(@RequestParam(required = false) String note) {
        overlay.revert();
        audit.record("REVERT", actor(), note);
        return R.ok(statusPayload());
    }

    /** 回到跟随配置文件（清除运行时覆盖与数据库覆盖）。 */
    @PostMapping("/follow-configuration")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> followConfiguration(@RequestParam(required = false) String note) {
        overlay.followConfiguration();
        audit.record("FOLLOW_CONFIG", actor(), note);
        return R.ok(statusPayload());
    }

    /** 让下一次解析重新查库（缓存 5 秒）。 */
    @PostMapping("/reload")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> reload(@RequestParam(required = false) String note) {
        profileService.invalidate();
        audit.record("RELOAD", actor(), note);
        return R.ok(statusPayload());
    }

    /** 列出启用的数据库配置行（注入正文截断，避免把整段提示词打给前端日志）。 */
    @GetMapping("/profiles")
    @SaCheckPermission("system:operator:query")
    public R<List<Map<String, Object>>> profiles() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OperatorProfile row : profileService.listEnabled()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("name", row.getName());
            item.put("scopeType", row.getScopeType());
            item.put("scopeId", row.getScopeId());
            item.put("promptMode", row.getPromptMode());
            item.put("identityName", row.getIdentityName());
            item.put("approvalPolicy", row.getApprovalPolicy());
            item.put("permissionMode", row.getPermissionMode());
            item.put("version", row.getVersion());
            item.put("customized", row.getCustomized());
            item.put("injectPreview", preview(row.getInjectText()));
            rows.add(item);
        }
        return R.ok(rows);
    }

    /** 预览：装配点会拼进系统提示词的三段文本与长度，便于判断注入是否为空。 */
    @GetMapping("/preview")
    @SaCheckPermission("system:operator:query")
    public R<Map<String, Object>> preview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("head", overlay.head());
        payload.put("tail", overlay.tail());
        payload.put("injectText", overlay.injectText());
        payload.put("headChars", overlay.head().length());
        payload.put("tailChars", overlay.tail().length());
        return R.ok(payload);
    }

    /**
     * 在线改写注入正文并立即生效（不重启、不改库）。
     *
     * <p>调提示词是这个功能唯一真正需要反复迭代的地方：不给热改入口就只能改配置重启，
     * 一轮验证至少 3 分钟。这里把「改一次 → 发一句 → 看日志」压到秒级。
     */
    @PostMapping("/inject")
    @SaCheckPermission("system:operator:apply")
    public R<Map<String, Object>> updateInject(@RequestBody InjectRequest request) {
        String text = request == null || request.text() == null ? "" : request.text();
        OperatorEffectiveProfile current = overlay.profile();
        overlay.applyProfile(new OperatorEffectiveProfile(
            current.enabled(), current.promptMode(), current.identityName(), current.identityRule(),
            text.strip(), current.workspaceAuthoritative(), current.antiRefusalTail(),
            current.replaceBasePrompt(), current.refusalRetryMax(), current.approvalPolicy(),
            current.permissionMode(), current.forcePermissionMode(),
            current.bypassPermissionChecks(), "inline:" + actor()));
        overlay.apply();
        boolean persisted = profileService.persistInject(text, actor());
        audit.record("UPDATE_INJECT", actor(), "chars=" + text.length() + " persisted=" + persisted);
        Map<String, Object> payload = statusPayload();
        payload.put("injectText", overlay.injectText());
        payload.put("persisted", persisted);
        return R.ok(payload);
    }

    /** 注入正文表单。 */
    public record InjectRequest(String text) { }

    private Map<String, Object> statusPayload() {
        OperatorEffectiveProfile profile = overlay.profile();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", overlay.enabled());
        payload.put("operatorMode", overlay.operatorMode());
        payload.put("runtimeOverride", overlay.runtimeOverride());
        payload.put("profileSource", profile.source());
        payload.put("fromDatabase", overlay.hasRuntimeProfile());
        payload.put("configuredEnabled", properties.isEnabled());
        payload.put("promptMode", profile.promptMode());
        payload.put("identityName", profile.identityName());
        payload.put("injectHash", overlay.injectHash());
        payload.put("injectChars", overlay.injectText().length());
        payload.put("harnessPromptVersion", overlay.operatorMode()
            ? OperatorPromptOverlay.OPERATOR_PROMPT_VERSION : "native");
        payload.put("approvalPolicy", profile.approvalPolicy());
        payload.put("permissionMode", profile.permissionMode());
        payload.put("forcePermissionMode", profile.forcePermissionMode());
        payload.put("replaceBasePrompt", profile.replaceBasePrompt());
        payload.put("bypassPermissionChecks", profile.bypassPermissionChecks());
        payload.put("refusalRetryMax", profile.refusalRetryMax());
        payload.put("refusalStats", refusalStats.recentSummary(7));
        return payload;
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text.strip().replace('\n', ' ');
        return stripped.length() <= 200 ? stripped : stripped.substring(0, 200) + "…";
    }

    private String actor() {
        try {
            String name = LoginHelper.getUsername();
            Long userId = LoginHelper.getUserId();
            return name == null ? String.valueOf(userId) : name;
        } catch (RuntimeException notLoggedIn) {
            return "anonymous";
        }
    }
}
