package org.ruoyi.domain.entity.operator;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 破甲拒绝统计行（表 {@code operator_refusal_stat}），按 天 × 场景 × 模型 聚合。
 *
 * <p>三个口径：
 * <ul>
 *   <li>{@code total}：首轮请求完成数（含长答案，判断拒绝率用）；</li>
 *   <li>{@code refused}：命中拒绝的轮次数（含重试轮仍拒绝，观察「越试越硬」）；</li>
 *   <li>{@code retryOk}：重试轮最终非拒绝放行数（衡量换框重做的成功率）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operator_refusal_stat")
public class OperatorRefusalStat extends TenantEntity {

    @TableId
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置行ID；运行态/配置驱动时无对应行，用 0 表示（保证唯一键能去重）。 */
    private Long profileId;

    private String model;

    /** chat.model | chat.agent | chat.mp */
    private String scene;

    private LocalDate statDate;

    private Integer total;

    private Integer refused;

    private Integer retryOk;
}
