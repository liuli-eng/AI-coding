package org.ruoyi.service.knowledge;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.mapper.knowledge.*;
import org.springframework.stereotype.Service;
import static org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.UNAVAILABLE;
@Service @RequiredArgsConstructor
public class KnowledgeAdminAccess {
    private final KnowledgeInfoMapper knowledge;
    private final KnowledgeAttachMapper documents;
    public KnowledgeInfo requireKnowledge(Long id,boolean lock){
        if(id==null||id<=0)throw new KnowledgeDocumentException(UNAVAILABLE);
        var q=new QueryWrapper<KnowledgeInfo>().eq("id",id).eq("tenant_id",KnowledgeSettingsService.tenant());
        if(lock)q.last("FOR UPDATE");
        var info=knowledge.selectOne(q);
        if(info==null)throw new KnowledgeDocumentException(UNAVAILABLE);
        return info;
    }
    public KnowledgeAttach requireDocument(Long id){return requireDocument(id,false);}
    public KnowledgeAttach requireDocument(Long id,boolean lock){
        if(id==null||id<=0)throw new KnowledgeDocumentException(UNAVAILABLE);
        var row=documents.selectOne(new QueryWrapper<KnowledgeAttach>().eq("id",id).eq("tenant_id",KnowledgeSettingsService.tenant()));
        if(row==null)throw new KnowledgeDocumentException(UNAVAILABLE);
        requireKnowledge(row.getKnowledgeId(),lock);
        if(lock){
            row=documents.selectOne(new QueryWrapper<KnowledgeAttach>().eq("id",id).eq("tenant_id",KnowledgeSettingsService.tenant()).last("FOR UPDATE"));
            if(row==null)throw new KnowledgeDocumentException(UNAVAILABLE);
        }
        return row;
    }
}
