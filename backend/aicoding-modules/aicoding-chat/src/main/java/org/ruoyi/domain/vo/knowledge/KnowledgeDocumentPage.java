package org.ruoyi.domain.vo.knowledge;
import java.util.List;
public record KnowledgeDocumentPage(List<KnowledgeAttachVo> rows,long total,KnowledgeDocumentStats statistics) {}
