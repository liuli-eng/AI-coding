package org.ruoyi.domain.vo.knowledge;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** The complete ordinary-user knowledge base response; never expose retrieval configuration. */
public record KnowledgeBaseSummaryVo(String id, String name, String description,
                                     int documentCount,
                                     @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime) {
}
