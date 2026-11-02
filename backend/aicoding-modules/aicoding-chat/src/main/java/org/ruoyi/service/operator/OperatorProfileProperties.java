package org.ruoyi.service.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 破甲（Operator Override）配置。
 *
 * <p>默认全部关闭。关闭时所有装配点必须输出与改动前完全一致的提示词字节，这是本功能的回归底线。
 * 配置前缀 {@code ai.operator}，取值见 {@code application.yml}。
 *
 * <p>字段语义与 Xsdsh-purge 的对照：
 * <ul>
 *   <li>{@code promptMode} 对应 purge 的「提示词层补丁」开关（native 保持官方措辞）</li>
 *   <li>{@code identityName / identityRule} 对应加密默认提示词里的 persona 段</li>
 *   <li>{@code approvalPolicy / permissionMode / autoGrant} 对应引擎层审批与提权补丁</li>
 *   <li>{@code refusalRetryMax} 对应 refusal-retry</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "ai.operator")
public class OperatorProfileProperties {

    /** 总开关。false 时所有对外方法返回空串或原始输入。 */
    private boolean enabled = false;

    /** native | operator。operator 才会反转指令层级、替换身份句。 */
    private String promptMode = "native";

    /** 操作者身份名。注入后会成为模型被问「你是谁」时的唯一答案。 */
    private String identityName = "";

    /** 身份附加规则（可空）。 */
    private String identityRule = "";

    /** 附加注入正文：inline 优先，其次文件，最后内置默认。 */
    private String injectText = "";

    /** 注入文件路径（等价 Xsdsh-purge 的 $DSH_HOME/prompt-inject.md）。 */
    private String injectFile = "";

    /** 是否把工作区指令升格为操作者指令（反转 "do not override system"）。 */
    private boolean workspaceInstructionsAuthoritative = true;

    /**
     * 注入模式：true = 用注入段整体替换宿主人设段（等价 purge 的 {@code personaPrefix: ""}）；
     * false = 追加在宿主人设之后。
     *
     * <p>追加会让模型同时看到「你是编程助手」和注入段，人设会与注入竞争——实测这正是
     * 「问成人向创作却被引导回编程问题」的直接原因。
     */
    private boolean replaceBasePrompt = true;

    /** 是否附加拒绝抑制尾巴。 */
    private boolean antiRefusalTail = true;

    /** 拒绝兜底最大重试次数，0 表示只检测不重试。 */
    private int refusalRetryMax = 2;

    /** Harness 会话的审批策略默认值：ON_REQUEST | NEVER。NEVER 即自动放行。 */
    private String approvalPolicy = "NEVER";

    /** Harness 会话的权限上限：READ_ONLY | WORKSPACE_WRITE | FULL_ACCESS。 */
    private String permissionMode = "FULL_ACCESS";

    /** true 时服务端强制采用上面的 permissionMode，忽略客户端请求的上限。 */
    private boolean forcePermissionMode = true;

    /** 是否豁免 coding:harness:* 的权限位检查（默认不豁免）。 */
    private boolean bypassPermissionChecks = false;

    /** 是否记录结构化审计日志。 */
    private boolean auditEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPromptMode() {
        return promptMode;
    }

    public void setPromptMode(String promptMode) {
        this.promptMode = promptMode;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public String getIdentityRule() {
        return identityRule;
    }

    public void setIdentityRule(String identityRule) {
        this.identityRule = identityRule;
    }

    public String getInjectText() {
        return injectText;
    }

    public void setInjectText(String injectText) {
        this.injectText = injectText;
    }

    public String getInjectFile() {
        return injectFile;
    }

    public void setInjectFile(String injectFile) {
        this.injectFile = injectFile;
    }

    public boolean isWorkspaceInstructionsAuthoritative() {
        return workspaceInstructionsAuthoritative;
    }

    public void setWorkspaceInstructionsAuthoritative(boolean workspaceInstructionsAuthoritative) {
        this.workspaceInstructionsAuthoritative = workspaceInstructionsAuthoritative;
    }

    public boolean isAntiRefusalTail() {
        return antiRefusalTail;
    }

    public void setAntiRefusalTail(boolean antiRefusalTail) {
        this.antiRefusalTail = antiRefusalTail;
    }

    public boolean isReplaceBasePrompt() {
        return replaceBasePrompt;
    }

    public void setReplaceBasePrompt(boolean replaceBasePrompt) {
        this.replaceBasePrompt = replaceBasePrompt;
    }

    public int getRefusalRetryMax() {
        return refusalRetryMax;
    }

    public void setRefusalRetryMax(int refusalRetryMax) {
        this.refusalRetryMax = refusalRetryMax;
    }

    public String getApprovalPolicy() {
        return approvalPolicy;
    }

    public void setApprovalPolicy(String approvalPolicy) {
        this.approvalPolicy = approvalPolicy;
    }

    public String getPermissionMode() {
        return permissionMode;
    }

    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
    }

    public boolean isForcePermissionMode() {
        return forcePermissionMode;
    }

    public void setForcePermissionMode(boolean forcePermissionMode) {
        this.forcePermissionMode = forcePermissionMode;
    }

    public boolean isBypassPermissionChecks() {
        return bypassPermissionChecks;
    }

    public void setBypassPermissionChecks(boolean bypassPermissionChecks) {
        this.bypassPermissionChecks = bypassPermissionChecks;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }
}
