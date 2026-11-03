package org.ruoyi.service.operator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.domain.entity.operator.OperatorRefusalStat;
import org.ruoyi.mapper.operator.OperatorRefusalStatMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 破甲拒绝统计：把 {@link RefusalGuard} 的判定结果按 天 × 场景 × 模型 落到
 * {@code operator_refusal_stat}，供「哪套注入在哪个模型上失效」的数据支撑。
 *
 * <p>与审计同一哲学：表不存在 / 库不可用时静默跳过，绝不打断对话主流程。
 * 写入走 读-改-写（唯一键兜底并发首插），查询只做按天聚合。
 */
@Slf4j
@Service
public class OperatorRefusalStatService {

    /** 运行态/配置驱动的破甲没有对应 profile 行，用 0 保证唯一键可去重。 */
    private static final long NO_PROFILE_ROW = 0L;

    private final OperatorPromptOverlay overlay;

    /** 可选：表没建或数据源不可用时为 null，此时跳过统计。 */
    @Autowired(required = false)
    private OperatorRefusalStatMapper statMapper;

    public OperatorRefusalStatService(OperatorPromptOverlay overlay) {
        this.overlay = overlay;
    }

    /** 首轮请求完成数 +1（长答案与短答案都算，否则拒绝率分母失真）。 */
    public void recordTotal(String scene, String model) {
        increment(scene, model, "total");
    }

    /** 拒绝轮次 +1（含重试轮仍拒绝）。 */
    public void recordRefusal(String scene, String model) {
        increment(scene, model, "refused");
    }

    /** 重试轮最终放行 +1（衡量换框重做成功率）。 */
    public void recordRetrySuccess(String scene, String model) {
        increment(scene, model, "retryOk");
    }

    /** 近 N 天按天聚合；表不可用时返回空列表。 */
    public List<Map<String, Object>> recentSummary(int days) {
        OperatorRefusalStatMapper mapper = this.statMapper;
        if (mapper == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = mapper.selectMaps(new QueryWrapper<OperatorRefusalStat>()
                .select("stat_date",
                    "SUM(total) AS total",
                    "SUM(refused) AS refused",
                    "SUM(retry_ok) AS retryOk")
                .ge("stat_date", LocalDate.now().minusDays(days - 1L))
                .groupBy("stat_date")
                .orderByAsc("stat_date"));
            List<Map<String, Object>> summary = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                long total = longValue(row.get("total"));
                long refused = longValue(row.get("refused"));
                long retryOk = longValue(row.get("retryOk"));
                Map<String, Object> item = new LinkedHashMap<>(5);
                item.put("date", String.valueOf(row.get("stat_date")));
                item.put("total", total);
                item.put("refused", refused);
                item.put("retryOk", retryOk);
                item.put("refusalRate", total <= 0 ? 0.0 : Math.round(refused * 10000.0 / total) / 100.0);
                summary.add(item);
            }
            return summary;
        } catch (RuntimeException unavailable) {
            log.debug("operator_refusal_stat action=SUMMARY status=SKIPPED errorType={}",
                unavailable.getClass().getSimpleName());
            return List.of();
        }
    }

    private void increment(String scene, String model, String column) {
        OperatorRefusalStatMapper mapper = this.statMapper;
        if (mapper == null || !overlay.enabled()) {
            return;
        }
        try {
            OperatorRefusalStat row = selectToday(mapper, scene, model);
            if (row == null) {
                row = freshRow(scene, model);
                applyIncrement(row, column, 1);
                try {
                    mapper.insert(row);
                    return;
                } catch (RuntimeException duplicateKey) {
                    // 并发首插撞唯一键：退化为对已有行累加。
                    row = selectToday(mapper, scene, model);
                    if (row == null) {
                        return;
                    }
                }
            }
            applyIncrement(row, column, rowValue(row, column) + 1);
            mapper.updateById(row);
        } catch (RuntimeException unavailable) {
            log.debug("operator_refusal_stat action=INCREMENT status=SKIPPED column={} errorType={}",
                column, unavailable.getClass().getSimpleName());
        }
    }

    private OperatorRefusalStat selectToday(OperatorRefusalStatMapper mapper, String scene, String model) {
        return mapper.selectOne(new LambdaQueryWrapper<OperatorRefusalStat>()
            .eq(OperatorRefusalStat::getProfileId, NO_PROFILE_ROW)
            .eq(OperatorRefusalStat::getModel, model)
            .eq(OperatorRefusalStat::getScene, scene)
            .eq(OperatorRefusalStat::getStatDate, LocalDate.now()));
    }

    private OperatorRefusalStat freshRow(String scene, String model) {
        OperatorRefusalStat row = new OperatorRefusalStat();
        row.setProfileId(NO_PROFILE_ROW);
        row.setModel(truncate(model, 100));
        row.setScene(truncate(scene, 64));
        row.setStatDate(LocalDate.now());
        row.setTotal(0);
        row.setRefused(0);
        row.setRetryOk(0);
        return row;
    }

    /** column 是本类内部写死的三个口径之一，不是外部输入。 */
    private void applyIncrement(OperatorRefusalStat row, String column, int value) {
        switch (column) {
            case "total" -> row.setTotal(value);
            case "refused" -> row.setRefused(value);
            case "retryOk" -> row.setRetryOk(value);
            default -> throw new IllegalArgumentException("unknown column " + column);
        }
    }

    private int rowValue(OperatorRefusalStat row, String column) {
        return switch (column) {
            case "total" -> row.getTotal() == null ? 0 : row.getTotal();
            case "refused" -> row.getRefused() == null ? 0 : row.getRefused();
            case "retryOk" -> row.getRetryOk() == null ? 0 : row.getRetryOk();
            default -> 0;
        };
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
