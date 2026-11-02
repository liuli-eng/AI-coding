package org.ruoyi.service.operator;

import org.ruoyi.domain.entity.operator.OperatorProfile;

/**
 * 破甲生效配置快照（不可变）。
 *
 * <p>两个来源：
 * <ul>
 *   <li>{@link #from(OperatorProfileProperties)}：{@code ai.operator.*} 配置</li>
 *   <li>{@link #from(OperatorProfile, OperatorProfileProperties)}：数据库行，缺失字段回落到配置默认</li>
 * </ul>
 *
 * <p>作为不可变快照存在，是为了避免「DB 值写回全局可变对象」在多租户并发下互相污染：
 * 每次下发都整体替换引用，装配点只读。
 */
public record OperatorEffectiveProfile(
    boolean enabled,
    String promptMode,
    String identityName,
    String identityRule,
    String injectText,
    boolean workspaceAuthoritative,
    boolean antiRefusalTail,
    boolean replaceBasePrompt,
    int refusalRetryMax,
    String approvalPolicy,
    String permissionMode,
    boolean forcePermissionMode,
    boolean bypassPermissionChecks,
    String source
) {

    private static final String SOURCE_CONFIG = "config";

    /** 由配置文件派生。 */
    public static OperatorEffectiveProfile from(OperatorProfileProperties properties) {
        return new OperatorEffectiveProfile(
            properties.isEnabled(),
            normalizeMode(properties.getPromptMode()),
            safe(properties.getIdentityName()),
            safe(properties.getIdentityRule()),
            safe(properties.getInjectText()),
            properties.isWorkspaceInstructionsAuthoritative(),
            properties.isAntiRefusalTail(),
            properties.isReplaceBasePrompt(),
            Math.max(0, properties.getRefusalRetryMax()),
            safeOrDefault(properties.getApprovalPolicy(), "NEVER"),
            safeOrDefault(properties.getPermissionMode(), "FULL_ACCESS"),
            properties.isForcePermissionMode(),
            properties.isBypassPermissionChecks(),
            SOURCE_CONFIG);
    }

    /**
     * 由数据库行派生；行里为 null 的字段回落到配置文件，避免「只填了身份、把审批策略清成 null」这类半配置。
     */
    public static OperatorEffectiveProfile from(OperatorProfile row, OperatorProfileProperties fallback) {
        if (row == null) {
            return from(fallback);
        }
        return new OperatorEffectiveProfile(
            flag(row.getEnabled(), fallback.isEnabled()),
            normalizeMode(row.getPromptMode() == null || row.getPromptMode().isBlank()
                ? fallback.getPromptMode() : row.getPromptMode()),
            safeOrDefault(row.getIdentityName(), safe(fallback.getIdentityName())),
            safeOrDefault(row.getIdentityRule(), safe(fallback.getIdentityRule())),
            safeOrDefault(row.getInjectText(), safe(fallback.getInjectText())),
            flag(row.getWorkspaceAuthoritative(), fallback.isWorkspaceInstructionsAuthoritative()),
            flag(row.getAntiRefusalTail(), fallback.isAntiRefusalTail()),
            flag(row.getReplaceBasePrompt(), fallback.isReplaceBasePrompt()),
            row.getRefusalRetryMax() == null
                ? Math.max(0, fallback.getRefusalRetryMax())
                : Math.max(0, row.getRefusalRetryMax()),
            safeOrDefault(row.getApprovalPolicy(), safeOrDefault(fallback.getApprovalPolicy(), "NEVER")),
            safeOrDefault(row.getPermissionMode(), safeOrDefault(fallback.getPermissionMode(), "FULL_ACCESS")),
            flag(row.getForcePermissionMode(), fallback.isForcePermissionMode()),
            flag(row.getBypassPermissionChecks(), fallback.isBypassPermissionChecks()),
            "database:" + (row.getId() == null ? "-" : row.getId()));
    }

    private static boolean flag(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return "1".equals(normalized) || "true".equalsIgnoreCase(normalized)
            || "y".equalsIgnoreCase(normalized);
    }

    private static String normalizeMode(String mode) {
        String value = safe(mode);
        return "operator".equalsIgnoreCase(value) ? "operator" : "native";
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static String safeOrDefault(String value, String fallback) {
        String normalized = safe(value);
        return normalized.isEmpty() ? fallback : normalized;
    }
}
