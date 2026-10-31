package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeParseFailureRecorder {
    private final KnowledgeAttachMapper mapper;

    /** Upload already committed; use a fresh transaction and never overwrite a newer file/parse. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(Long id,Long ossId,String message){
        mapper.update(null,new UpdateWrapper<KnowledgeAttach>().eq("id",id).eq("oss_id",ossId)
            .eq("status",0).set("status",3).set("remark",message));
    }
}
