package org.ruoyi.service.knowledge;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.knowledge.KnowledgeInfoUploadBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.KnowledgeSettingsVo;
import org.ruoyi.mapper.knowledge.*;
import org.ruoyi.service.knowledge.impl.KnowledgeAttachServiceImpl;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.ruoyi.service.vector.VectorStoreService;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("dev")
class KnowledgeDocumentUploadTest {
 private final KnowledgeAttachMapper mapper=mock(KnowledgeAttachMapper.class);
 private final KnowledgeSettingsService settings=spy(new KnowledgeSettingsService(mock(KnowledgeSettingsMapper.class)));
 private final KnowledgeAdminAccess access=mock(KnowledgeAdminAccess.class);
 private final OssService oss=mock(OssService.class);
 private final VectorStoreService vectors=mock(VectorStoreService.class);
 private final KnowledgeFragmentMapper fragments=mock(KnowledgeFragmentMapper.class);
 private final KnowledgeRetrievalService retrieval=mock(KnowledgeRetrievalService.class);
 private final KnowledgeAttachServiceImpl service=new KnowledgeAttachServiceImpl(mapper,null,fragments,null,null,vectors,oss,retrieval,settings,access,mock(KnowledgeParseFailureRecorder.class),new KnowledgeDocumentStorage(oss));
 private MockedStatic<LoginHelper> login;private MockedStatic<TenantHelper> tenant;
 @BeforeEach void setup(){
  login=mockStatic(LoginHelper.class);tenant=mockStatic(TenantHelper.class);
  login.when(LoginHelper::isLogin).thenReturn(true);login.when(LoginHelper::getUserId).thenReturn(7L);tenant.when(TenantHelper::getTenantId).thenReturn("000007");
  doReturn(new KnowledgeSettingsVo("name",Set.of("txt"),false,"请检查文档",0.5,0,null,null)).when(settings).current();
  var object=new OssDTO();object.setOssId(88L);object.setOriginalName("a.txt");object.setFileSuffix(".txt");when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenReturn(object);
  when(mapper.insert(any(KnowledgeAttach.class))).thenAnswer(c->{((KnowledgeAttach)c.getArgument(0)).setId(1L);return 1;});
 }
 @AfterEach void cleanup(){login.close();tenant.close();}
 private KnowledgeInfoUploadBo request(){var bo=new KnowledgeInfoUploadBo();bo.setKnowledgeId(42L);bo.setAutoParse(false);bo.setFile(new MockMultipartFile("file","a.txt","text/plain","new content".getBytes()));return bo;}
 private KnowledgeAttach old(int status){var row=new KnowledgeAttach();row.setId(1L);row.setKnowledgeId(42L);row.setOssId(77L);row.setDocId("old");row.setName("a.txt");row.setStatus(status);row.setBusinessState("0");when(mapper.selectOne(any())).thenReturn(row);return row;}
 private void allowOverwrite(){doReturn(new KnowledgeSettingsVo("name",Set.of("txt"),true,"请检查文档",0.5,0,null,null)).when(settings).current();}
 @Test void disabledFormatAndDuplicateContentDoNotCreateObjects(){
  var request=request();request.setFile(new MockMultipartFile("file","a.pdf","application/pdf",new byte[]{1}));
  assertThrows(KnowledgeDocumentException.class,()->service.upload(request));verifyNoInteractions(oss);
  when(mapper.exists(any())).thenReturn(true);assertThrows(KnowledgeDocumentException.class,()->service.upload(request()));verifyNoInteractions(oss);
 }
 @Test void sameNameIsRejectedByDefaultAndParsingCannotBeOverwritten(){
  old(2);assertEquals(KnowledgeDocumentException.Reason.SAME_NAME,assertThrows(KnowledgeDocumentException.class,()->service.upload(request())).reason());
  allowOverwrite();old(1);assertEquals(KnowledgeDocumentException.Reason.PARSING,assertThrows(KnowledgeDocumentException.class,()->service.upload(request())).reason());verifyNoInteractions(oss,vectors);
 }
 @Test void failedNewObjectUploadPreservesOldFileAndChunks(){
  allowOverwrite();old(2);when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenThrow(new IllegalStateException("private-storage-detail"));
  assertThrows(KnowledgeDocumentException.class,()->service.upload(request()));verify(oss,never()).deleteFile(anyLong());verifyNoInteractions(vectors,fragments);verify(mapper,never()).updateById(any(KnowledgeAttach.class));
 }
 @Test void failedDatabaseInsertRemovesOnlyNewObject(){
  when(mapper.insert(any(KnowledgeAttach.class))).thenThrow(new IllegalStateException("db failure"));
  assertThrows(RuntimeException.class,()->service.upload(request()));verify(oss).deleteFile(88L);verify(oss,never()).deleteFile(77L);
 }
 @Test void rollbackCleansOnlyNewObjectAndLeavesOldVectorsIntact(){
  allowOverwrite();old(2);when(mapper.updateById(any(KnowledgeAttach.class))).thenReturn(1);
  org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
  try{
   service.upload(request());verify(oss,never()).deleteFile(anyLong());verifyNoInteractions(vectors,retrieval);
   for(var sync:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
   verify(oss).deleteFile(88L);verify(oss,never()).deleteFile(77L);verifyNoInteractions(vectors,retrieval);
  }finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
 }
 @Test void committedReplacementCleansOldObjectsEvenIfCacheServiceFails(){
  allowOverwrite();old(2);when(mapper.updateById(any(KnowledgeAttach.class))).thenReturn(1);
  doThrow(new IllegalStateException("cache unavailable")).when(retrieval).invalidateKnowledge("42");
  org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
  try{
   service.upload(request());verifyNoInteractions(vectors);
   for(var sync:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())assertDoesNotThrow(sync::afterCommit);
   verify(vectors).removeByDocId("old","42");verify(oss).deleteFile(77L);verify(oss,never()).deleteFile(88L);
  }finally{org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();}
 }
 @Test void successfulReplacementResetsParsingAndInvalidatesCachedOldText(){
  allowOverwrite();var old=old(2);when(mapper.updateById(any(KnowledgeAttach.class))).thenReturn(1);
  service.upload(request());assertEquals(88L,old.getOssId());assertEquals(0,old.getStatus());assertNotEquals("old",old.getDocId());
  verify(oss).deleteFile(77L);verify(retrieval).invalidateKnowledge("42");verify(vectors).removeByDocId("old","42");
 }
}
