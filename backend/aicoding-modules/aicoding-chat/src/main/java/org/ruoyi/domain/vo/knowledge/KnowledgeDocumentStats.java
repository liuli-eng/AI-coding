package org.ruoyi.domain.vo.knowledge;
import lombok.Data;
@Data public class KnowledgeDocumentStats {
    private long waiting;
    private long processing;
    private long completed;
    private long failed;
    private long total;
}
