package org.ruoyi.service.knowledge;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("dev")
class KnowledgeDocumentsTest {
 @Test void unavailableDocumentCannotReachAnotherTenantsKnowledge() {
  var docs=mock(KnowledgeAttachMapper.class);var knowledge=mock(KnowledgeInfoMapper.class);
  var access=new KnowledgeAdminAccess(knowledge,docs);
  try(var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)){
   login.when(LoginHelper::isLogin).thenReturn(true);login.when(LoginHelper::getUserId).thenReturn(7L);tenant.when(TenantHelper::getTenantId).thenReturn("000007");
   assertThrows(KnowledgeDocumentException.class,()->access.requireDocument(42L));verifyNoInteractions(knowledge);
  }
 }
 @Test void disabledDocumentsCannotLeakFromAlreadyCachedResults() {
  var mapper=mock(KnowledgeAttachMapper.class);
  var guard=new KnowledgeDocumentAvailability(mapper);
  var allowed=KnowledgeRetrievalVo.builder().docId("on").content("allowed").build();
  var disabled=KnowledgeRetrievalVo.builder().docId("off").content("secret").build();
  var unknown=KnowledgeRetrievalVo.builder().content("unknown origin").build();
  var on=new KnowledgeAttach();on.setDocId("on");on.setBusinessState("0");var off=new KnowledgeAttach();off.setDocId("off");off.setBusinessState("1");
  when(mapper.documentStates(42L)).thenReturn(List.of(on,off));
  assertEquals(List.of(allowed),guard.filter("42",List.of(disabled,allowed,unknown)));
  off.setBusinessState("0");
  assertEquals(2,guard.filter("42",List.of(disabled,allowed)).size());
  when(mapper.documentStates(42L)).thenReturn(List.of(on));
  var blank=KnowledgeRetrievalVo.builder().docId(" ").content("legacy").build();
  assertEquals(List.of(allowed),guard.filter("42",List.of(disabled,allowed,unknown,blank)));
  when(mapper.documentStates(42L)).thenReturn(List.of());
  assertTrue(guard.filter("42",List.of(disabled,allowed,unknown)).isEmpty());
 }
}
