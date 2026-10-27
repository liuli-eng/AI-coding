package org.ruoyi.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.ruoyi.domain.entity.chat.ChatQuestion;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import java.util.Date;

public interface ChatQuestionMapper extends BaseMapper<ChatQuestion> {
    @Select("""
        SELECT COUNT(*) AS total,
          COALESCE(SUM(answer_status = 'success'), 0) AS success,
          COALESCE(SUM(answer_status = 'failed'), 0) AS failed,
          COALESCE(SUM(retrieval_status = 'no_match'), 0) AS unmatched,
          COALESCE(SUM(review_status = 'pending'), 0) AS pending,
          COALESCE(SUM(answer_status = 'running'), 0) AS running
        FROM chat_question WHERE started_at >= #{begin} AND started_at < #{end}
        """)
    Map<String, Object> totals(@Param("begin") Date begin, @Param("end") Date end);

    @Select("""
        SELECT model_name AS modelName, COUNT(*) AS calls,
          COALESCE(SUM(answer_status = 'success'), 0) AS success,
          COALESCE(SUM(answer_status = 'failed'), 0) AS failed,
          AVG(duration_ms) AS averageDurationMs
        FROM chat_question WHERE started_at >= #{begin} AND started_at < #{end}
        GROUP BY model_name ORDER BY calls DESC LIMIT 20
        """)
    List<Map<String, Object>> models(@Param("begin") Date begin, @Param("end") Date end);
}
