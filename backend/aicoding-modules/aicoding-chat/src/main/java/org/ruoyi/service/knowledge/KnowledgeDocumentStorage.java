package org.ruoyi.service.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.service.OssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Commit OSS metadata separately so document rollback compensation can still locate the object. */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentStorage {
    private final OssService oss;

    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public OssDTO upload(MultipartFile file){
        return oss.uploadFile(file);
    }
}
