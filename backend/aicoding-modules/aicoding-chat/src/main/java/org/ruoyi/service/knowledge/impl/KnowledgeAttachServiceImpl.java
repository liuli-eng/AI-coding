package org.ruoyi.service.knowledge.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.enums.KnowledgeAttachStatus;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.core.utils.SpringUtils;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo;
import org.ruoyi.domain.bo.knowledge.KnowledgeInfoUploadBo;
import org.ruoyi.domain.bo.vector.StoreEmbeddingBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.entity.knowledge.KnowledgeFragment;
import org.ruoyi.domain.vo.knowledge.DocFragmentCountVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeAttachVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeReparseVo;
import org.ruoyi.factory.ResourceLoaderFactory;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.mapper.knowledge.KnowledgeFragmentMapper;
import org.ruoyi.service.knowledge.IKnowledgeAttachService;
import org.ruoyi.service.knowledge.IKnowledgeInfoService;
import org.ruoyi.service.knowledge.ResourceLoader;
import org.ruoyi.service.knowledge.DocumentSplitConfig;
import org.ruoyi.service.vector.VectorStoreService;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库附件Service业务层处理
 *
 * @author ageerle
 * @date 2025-12-17
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeAttachServiceImpl implements IKnowledgeAttachService {

    private final KnowledgeAttachMapper baseMapper;
    private final IKnowledgeInfoService knowledgeInfoService;
    private final KnowledgeFragmentMapper knowledgeFragmentMapper;
    private final IChatModelService chatModelService;
    private final ResourceLoaderFactory resourceLoaderFactory;
    private final VectorStoreService vectorStoreService;
    private final OssService ossService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final org.ruoyi.service.knowledge.KnowledgeSettingsService settings;
    private final org.ruoyi.service.knowledge.KnowledgeAdminAccess access;
    private final org.ruoyi.service.knowledge.KnowledgeParseFailureRecorder parseFailure;
    private final org.ruoyi.service.knowledge.KnowledgeDocumentStorage storage;

    @Override
    public KnowledgeAttachVo queryById(Long id) {
        access.requireDocument(id);
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<KnowledgeAttachVo> queryPageList(KnowledgeAttachBo bo, PageQuery pageQuery) {
        if(bo.getKnowledgeId()!=null)access.requireKnowledge(bo.getKnowledgeId(),false);
        Page<KnowledgeAttachVo> result = baseMapper.workbench(pageQuery.build(), org.ruoyi.service.knowledge.KnowledgeSettingsService.tenant(), bo);
        fillFragmentCount(result.getRecords());
        return TableDataInfo.build(result);
    }

    @Override
    public List<KnowledgeAttachVo> queryList(KnowledgeAttachBo bo) {
        if(bo.getKnowledgeId()!=null)access.requireKnowledge(bo.getKnowledgeId(),false);
        List<KnowledgeAttachVo> list = baseMapper.workbench(new Page<KnowledgeAttachVo>(1,-1,false), org.ruoyi.service.knowledge.KnowledgeSettingsService.tenant(), bo).getRecords();
        fillFragmentCount(list);
        return list;
    }

    private void fillFragmentCount(List<KnowledgeAttachVo> records) {
        if (records == null || records.isEmpty()) return;
        List<String> docIds = records.stream()
            .map(KnowledgeAttachVo::getDocId)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        if (docIds.isEmpty()) return;
        List<DocFragmentCountVo> countList = knowledgeFragmentMapper.selectFragmentCountByDocIds(docIds);
        Map<String, Integer> countMap = countList.stream()
            .collect(Collectors.toMap(DocFragmentCountVo::getDocId, DocFragmentCountVo::getFragmentCount, (k1, k2) -> k1));
        for (KnowledgeAttachVo vo : records) {
            vo.setFragmentCount(countMap.getOrDefault(vo.getDocId(), 0));
        }
    }

    @Override
    public Boolean insertByBo(KnowledgeAttachBo bo) {
        // Files are created only by the validated multipart upload path.
        throw new org.ruoyi.service.knowledge.KnowledgeDocumentException(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.INVALID_FILE);
    }

    @Override
    public Boolean updateByBo(KnowledgeAttachBo bo) {
        var existing=access.requireDocument(bo.getId());
        var update=new KnowledgeAttach();update.setId(existing.getId());
        update.setRemark(bo.getRemark());
        return baseMapper.updateById(update)>0;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(ids==null||ids.isEmpty())return false;
        var attaches=ids.stream().distinct().sorted().map(id->access.requireDocument(id,true)).toList();
        for(var attach:attaches)if(Integer.valueOf(1).equals(attach.getStatus()))throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.PARSING);
        for(var attach:attaches){
            String kid=String.valueOf(attach.getKnowledgeId());
            knowledgeFragmentMapper.delete(Wrappers.<KnowledgeFragment>lambdaQuery().eq(KnowledgeFragment::getDocId,attach.getDocId()));
        }
        boolean deleted=baseMapper.deleteByIds(ids)>0;
        if(deleted)afterCommit(()->attaches.forEach(a->{
            cleanupObject(a.getOssId());
            cleanupVector(a.getDocId(),String.valueOf(a.getKnowledgeId()));
            invalidateCache(String.valueOf(a.getKnowledgeId()));
        }));
        return deleted;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void upload(KnowledgeInfoUploadBo bo) {
        String tenant=org.ruoyi.service.knowledge.KnowledgeSettingsService.tenant();
        access.requireKnowledge(bo.getKnowledgeId(),true); // Serialize competing uploads for one library.
        var policy=settings.current();
        MultipartFile file=bo.getFile();String suffix=settings.validateUpload(file,policy);
        String name=org.ruoyi.service.knowledge.KnowledgeSettingsService.filename(file);
        String fileHash;
        long fileSize;
        try(InputStream input=file.getInputStream()){
            byte[] bytes=input.readNBytes(org.ruoyi.service.knowledge.KnowledgeSettingsService.MAX_FILE_SIZE+1);
            fileSize=bytes.length;
            if(fileSize==0)throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.EMPTY_FILE);
            if(fileSize>org.ruoyi.service.knowledge.KnowledgeSettingsService.MAX_FILE_SIZE)throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.FILE_TOO_LARGE);
            fileHash=DigestUtil.sha256Hex(bytes);
        }catch(java.io.IOException e){throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.READ_FAILED);}
        boolean duplicate=baseMapper.exists(Wrappers.<KnowledgeAttach>lambdaQuery().eq(KnowledgeAttach::getKnowledgeId,bo.getKnowledgeId()).eq(KnowledgeAttach::getTenantId,tenant).eq(KnowledgeAttach::getFileHash,fileHash));
        if(duplicate)throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.DUPLICATE);
        var old=baseMapper.selectOne(Wrappers.<KnowledgeAttach>lambdaQuery().eq(KnowledgeAttach::getKnowledgeId,bo.getKnowledgeId()).eq(KnowledgeAttach::getTenantId,tenant).eq(KnowledgeAttach::getName,name).last("FOR UPDATE"));
        if(old!=null&&!policy.overwriteSameName())throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.SAME_NAME);
        if(old!=null&&Integer.valueOf(1).equals(old.getStatus()))throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.PARSING);
        OssDTO object;
        try{object=storage.upload(file);}catch(RuntimeException error){throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.STORAGE_FAILED);}
        if(object==null||object.getOssId()==null)throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.STORAGE_FAILED);
        Long previousObject=old==null?null:old.getOssId();String previousDoc=old==null?null:old.getDocId();
        var row=old==null?new KnowledgeAttach():old;
        row.setKnowledgeId(bo.getKnowledgeId());row.setTenantId(tenant);row.setOssId(object.getOssId());row.setDocId(RandomUtil.randomString(20));
        row.setFileHash(fileHash);row.setName(name);row.setType(suffix);row.setFileSize(fileSize);row.setStatus(0);row.setRemark(null);
        row.setCreateBy(org.ruoyi.common.satoken.utils.LoginHelper.getUserId());row.setCreateTime(new Date());
        if(old==null)row.setBusinessState("0");
        try{
            int saved=old==null?baseMapper.insert(row):baseMapper.updateById(row);
            if(saved!=1)throw documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.STORAGE_FAILED);
            if(previousDoc!=null){
                knowledgeFragmentMapper.delete(Wrappers.<KnowledgeFragment>lambdaQuery().eq(KnowledgeFragment::getDocId,previousDoc));
            }
        }catch(RuntimeException failure){cleanupObject(object.getOssId());throw failure;}
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)cleanupObject(object.getOssId());}
            });
        }
        afterCommit(()->{
            if(previousObject!=null)cleanupObject(previousObject);
            if(previousDoc!=null)cleanupVector(previousDoc,String.valueOf(bo.getKnowledgeId()));
            invalidateCache(String.valueOf(bo.getKnowledgeId()));
            if(Boolean.TRUE.equals(bo.getAutoParse())){
                try{SpringUtils.getBean(IKnowledgeAttachService.class).parse(row.getId());}
                catch(RuntimeException error){
                    try{parseFailure.record(row.getId(),row.getOssId(),policy.failureMessage());}
                    catch(RuntimeException recordError){log.warn("Document submission failure state could not be recorded, id={}",row.getId());}
                    log.warn("Document parse submission failed, id={}, errorType={}",row.getId(),error.getClass().getSimpleName());
                }
            }
        });
    }
    private org.ruoyi.service.knowledge.KnowledgeDocumentException documentError(org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason reason){return new org.ruoyi.service.knowledge.KnowledgeDocumentException(reason);}
    private void afterCommit(Runnable operation){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){operation.run();}});
        else operation.run();
    }
    private void cleanupVector(String docId,String knowledgeId){
        try{vectorStoreService.removeByDocId(docId,knowledgeId);}
        catch(RuntimeException error){log.warn("Document vector cleanup needs retry, knowledgeId={}, errorType={}",knowledgeId,error.getClass().getSimpleName());}
    }
    private void invalidateCache(String knowledgeId){
        try{knowledgeRetrievalService.invalidateKnowledge(knowledgeId);}
        catch(RuntimeException error){log.warn("Document cache invalidation failed, knowledgeId={}, errorType={}",knowledgeId,error.getClass().getSimpleName());}
    }
    private void cleanupObject(Long id){
        if(id==null)return;
        try{if(!Boolean.TRUE.equals(ossService.deleteFile(id)))log.warn("Document object cleanup needs retry, id={}",id);}
        catch(RuntimeException e){log.warn("Document object cleanup needs retry, id={}, errorType={}",id,e.getClass().getSimpleName());}
    }

    @Async("knowledgeParseExecutor")
    @Override
    public void parse(Long id) {
        KnowledgeAttach attach = baseMapper.selectById(id);
        if (attach == null || KnowledgeAttachStatus.PARSING.getCode().equals(attach.getStatus())) {
            return;
        }

        int claimed = baseMapper.update(null, Wrappers.<KnowledgeAttach>lambdaUpdate()
            .set(KnowledgeAttach::getStatus, KnowledgeAttachStatus.PARSING.getCode())
            .set(KnowledgeAttach::getRemark, null)
            .eq(KnowledgeAttach::getId, id)
            .ne(KnowledgeAttach::getStatus, KnowledgeAttachStatus.PARSING.getCode())
            .eq(KnowledgeAttach::getOssId, attach.getOssId()));
        if (claimed == 0) return;

        try {
            attach.setStatus(KnowledgeAttachStatus.PARSING.getCode()); // 解析中

            log.info("开始解析知识库文档... id: {}, docId: {}", id, attach.getDocId());

            Long knowledgeId = attach.getKnowledgeId();
            String docId = attach.getDocId();
            KnowledgeInfoVo knowledgeInfoVo = knowledgeInfoService.queryById(knowledgeId);
            if (knowledgeInfoVo == null) {
                throw new ServiceException("知识库不存在: " + knowledgeId);
            }
            int blockSize = knowledgeInfoVo.getTextBlockSize() == null
                ? DocumentSplitConfig.DEFAULT_BLOCK_SIZE : knowledgeInfoVo.getTextBlockSize().intValue();
            int overlap = knowledgeInfoVo.getOverlapChar() == null
                ? DocumentSplitConfig.DEFAULT_OVERLAP : knowledgeInfoVo.getOverlapChar().intValue();
            DocumentSplitConfig splitConfig = new DocumentSplitConfig(
                knowledgeInfoVo.getSeparator(), blockSize, overlap, attach.getType());

            // 获取文件信息并下载
            List<OssDTO> ossDTOs = ossService.selectByIds(String.valueOf(attach.getOssId()));
            if (ossDTOs == null || ossDTOs.isEmpty()) {
                throw new RuntimeException("未找到对应的 OSS 文件信息");
            }
            OssDTO ossDTO = ossDTOs.get(0);
            String content;
            ResourceLoader resourceLoader = resourceLoaderFactory.getLoaderByFileType(attach.getType());
            try (InputStream inputStream = new URL(ossDTO.getUrl()).openStream()) {
                content = resourceLoader.getContent(inputStream);
            }
            List<String> chunkList = resourceLoader.getChunkList(content, splitConfig);

            if (CollUtil.isEmpty(chunkList)) {
                throw new RuntimeException("文档分片结果为空，请检查文档内容或分片器是否支持该文件类型");
            }

            // 重新解析前先清理旧的向量数据，避免向量重复累积
            List<String> fids = new ArrayList<>();
            List<KnowledgeFragment> knowledgeFragmentList = new ArrayList<>();
            for (int i = 0; i < chunkList.size(); i++) {
                String fid = RandomUtil.randomString(10);
                fids.add(fid);
                KnowledgeFragment knowledgeFragment = new KnowledgeFragment();
                knowledgeFragment.setKnowledgeId(knowledgeId);
                knowledgeFragment.setDocId(docId);
                knowledgeFragment.setFid(fid);
                knowledgeFragment.setIdx(i);
                knowledgeFragment.setContent(chunkList.get(i));
                knowledgeFragment.setCreateTime(new Date());
                knowledgeFragmentList.add(knowledgeFragment);
            }
            ChatModelVo chatModelVo = chatModelService.selectModelByName(knowledgeInfoVo.getEmbeddingModel());

            StoreEmbeddingBo storeEmbeddingBo = new StoreEmbeddingBo();
            storeEmbeddingBo.setKid(String.valueOf(knowledgeId));
            storeEmbeddingBo.setDocId(docId);
            storeEmbeddingBo.setFids(fids);
            storeEmbeddingBo.setChunkList(chunkList);
            storeEmbeddingBo.setVectorStoreName(knowledgeInfoVo.getVectorModel());
            storeEmbeddingBo.setEmbeddingModelName(knowledgeInfoVo.getEmbeddingModel());
            storeEmbeddingBo.setBaseUrl(chatModelVo.getApiHost());
            try {
                // 写入新向量前，先按 docId 清理该文档的旧向量：
                // 历史数据的片段 fid 为迁移脚本回填的 MD5 值，与向量库中实际存储的 fid 不一致，
                // 按 fid 删除无法命中旧向量，会导致重复向量累积；按 docId 清理对三种向量库均一致有效。
                vectorStoreService.removeByDocId(docId, String.valueOf(knowledgeId));
                vectorStoreService.storeEmbeddings(storeEmbeddingBo);
            } catch (Exception vectorError) {
                for (String newFid : fids) {
                    try {
                        vectorStoreService.removeByFid(newFid, String.valueOf(knowledgeId));
                    } catch (Exception cleanupError) {
                        log.error("补偿删除新向量失败, kid={}, fid={}", knowledgeId, newFid, cleanupError);
                    }
                }
                throw vectorError;
            }

            knowledgeFragmentMapper.delete(Wrappers.<KnowledgeFragment>lambdaQuery().eq(KnowledgeFragment::getDocId, docId));
            knowledgeFragmentMapper.insertBatch(knowledgeFragmentList);
            knowledgeRetrievalService.invalidateKnowledge(String.valueOf(knowledgeId));

            attach.setStatus(KnowledgeAttachStatus.COMPLETED.getCode()); // 已完成
            finishParsing(attach,KnowledgeAttachStatus.COMPLETED.getCode(),null);
            log.info("知识库文档解析、向量化并入库成功！id: {}", id);
        } catch (Exception e) {
            log.error("Document parse failed, id={}, errorType={}", id, e.getClass().getSimpleName());
            attach.setStatus(KnowledgeAttachStatus.FAILED.getCode()); // 失败
            String hint=org.ruoyi.service.knowledge.KnowledgeSettingsService.defaults().failureMessage();
            try{
                String tenant=attach.getTenantId();
                if(tenant!=null&&tenant.matches("[0-9]{1,5}"))tenant="0".repeat(6-tenant.length())+tenant;
                hint=settings.forTenant(tenant).failureMessage();
            }catch(RuntimeException ignored){/* Use the safe default if settings cannot be read. */}
            finishParsing(attach,KnowledgeAttachStatus.FAILED.getCode(),hint);
        }
    }

    private void finishParsing(KnowledgeAttach source,Integer status,String remark){
        baseMapper.update(null,Wrappers.<KnowledgeAttach>lambdaUpdate()
            .eq(KnowledgeAttach::getId,source.getId()).eq(KnowledgeAttach::getOssId,source.getOssId())
            .eq(KnowledgeAttach::getDocId,source.getDocId()).eq(KnowledgeAttach::getStatus,KnowledgeAttachStatus.PARSING.getCode())
            .set(KnowledgeAttach::getStatus,status).set(KnowledgeAttach::getRemark,remark));
    }

    @Override
    public KnowledgeReparseVo reparseKnowledge(Long knowledgeId) {
        access.requireKnowledge(knowledgeId,false);
        List<KnowledgeAttach> attachments = baseMapper.selectList(
            Wrappers.<KnowledgeAttach>lambdaQuery().eq(KnowledgeAttach::getKnowledgeId, knowledgeId).eq(KnowledgeAttach::getTenantId, org.ruoyi.service.knowledge.KnowledgeSettingsService.tenant()));
        int submitted = 0;
        int skipped = 0;
        IKnowledgeAttachService proxy = SpringUtils.getBean(IKnowledgeAttachService.class);
        for (KnowledgeAttach attachment : attachments) {
            if (KnowledgeAttachStatus.PARSING.getCode().equals(attachment.getStatus())) {
                skipped++;
            } else {
                proxy.parse(attachment.getId());
                submitted++;
            }
        }
        return new KnowledgeReparseVo(submitted, skipped, attachments.size());
    }
}
