package org.ruoyi.domain.entity.operator;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

/**
 * 破甲审计行（表 {@code operator_audit}）。
 *
 * <p>原先审计只落日志；现在每次 apply / revert / 改注入都会写一行到库里，
 * 便于按时间与操作者回溯「谁在什么时候改了破甲配置」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operator_audit")
public class OperatorAudit extends TenantEntity {

    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联的配置行，可为空（配置驱动时没有行）。 */
    private Long profileId;

    /** APPLY / REVERT / FOLLOW_CONFIG / RELOAD / UPDATE_INJECT / APPLY_FROM_DB / INJECT。 */
    private String action;

    private String actor;

    private String injectHash;

    private String promptVersion;

    private String detail;
}
