package org.ruoyi.domain.entity.operator;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

/**
 * 破甲配置行（表 {@code operator_profile}）。
 *
 * <p>作用域：0 全局 / 1 租户 / 2 用户 / 3 智能体。解析顺序为 用户 > 租户 > 全局，
 * 与 {@code ai.operator.*} 配置相比，本表的值优先。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operator_profile")
public class OperatorProfile extends TenantEntity {

    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 0 全局 / 1 租户 / 2 用户 / 3 智能体。 */
    private String scopeType;

    /** 作用域标识：全局为空串。 */
    private String scopeId;

    private String name;

    /** 0 否 / 1 是。 */
    private String enabled;

    /** native | operator。 */
    private String promptMode;

    /** inline | file | builtin。 */
    private String injectSource;

    private String injectText;

    private String injectHash;

    private String seedHash;

    /** 用户是否手改过注入正文。 */
    private String customized;

    private String identityName;

    private String identityRule;

    /** 工作区指令是否升格为操作者指令。 */
    private String workspaceAuthoritative;

    private String antiRefusalTail;

    /** 1 = 用注入段替换宿主人设段（等价 purge 的 personaPrefix: ""）。 */
    private String replaceBasePrompt;

    private Integer refusalRetryMax;

    private String approvalPolicy;

    private String permissionMode;

    private String forcePermissionMode;

    private String bypassPermissionChecks;

    private String auditEnabled;

    private Integer version;

    private String remark;
}
