package org.ruoyi.service.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class KnowledgeDocumentAvailability {
    private final KnowledgeAttachMapper mapper;
    public List<KnowledgeRetrievalVo> filter(String knowledgeId,List<KnowledgeRetrievalVo> results){
        var rows=mapper.documentStates(Long.valueOf(knowledgeId));
        var enabled=new HashSet<String>();
        for(var row:rows){
            if(!"1".equals(row.getBusinessState())&&row.getDocId()!=null&&!row.getDocId().isBlank())enabled.add(row.getDocId());
        }
        // Only verifiable current sources are eligible, including on a cache hit.
        return results.stream().filter(r->r.getDocId()!=null&&!r.getDocId().isBlank()&&enabled.contains(r.getDocId())).toList();
    }
}
