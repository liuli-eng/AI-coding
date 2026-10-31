package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeSettings;
import org.ruoyi.domain.vo.knowledge.KnowledgeSettingsVo;
import org.ruoyi.mapper.knowledge.KnowledgeSettingsMapper;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class KnowledgeSettingsPersistenceTest {
    @BeforeAll static void jsonContext(){var context=new org.springframework.context.support.GenericApplicationContext();context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,() -> new com.fasterxml.jackson.databind.ObjectMapper());context.refresh();new cn.hutool.extra.spring.SpringUtil().setApplicationContext(context);}
    @Test void saveAndResetStayInCurrentTenantAndRecordActor() {
        var mapper=mock(KnowledgeSettingsMapper.class);
        var service=new KnowledgeSettingsService(mapper);
        try(var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)){
            login.when(LoginHelper::isLogin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            tenant.when(TenantHelper::getTenantId).thenReturn("000007");
            when(mapper.insert(any())).thenAnswer(call->{
                KnowledgeSettings row=call.getArgument(0);
                assertEquals("000007",row.getTenantId());
                assertEquals(7L,row.getUpdateBy());
                assertNotNull(row.getUpdateTime());return 1;
            });
            service.save(KnowledgeSettingsService.defaults());
            verify(mapper).get("000007");
            when(mapper.update(any())).thenAnswer(call->{
                KnowledgeSettings row=call.getArgument(0);
                assertEquals("000007",row.getTenantId());assertEquals(3,row.getVersion());
                assertEquals("AI 编程助手",row.getSystemName());return 1;
            });
            service.reset(3);verify(mapper,times(2)).get("000007");
        }
    }
    @Test void staleVersionsAndSimultaneousFirstSaveReturnSafeConflict() {
        var mapper=mock(KnowledgeSettingsMapper.class);
        var service=new KnowledgeSettingsService(mapper);
        try(var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)){
            login.when(LoginHelper::isLogin).thenReturn(true);login.when(LoginHelper::getUserId).thenReturn(7L);
            tenant.when(TenantHelper::getTenantId).thenReturn("000007");
            assertEquals(KnowledgeDocumentException.Reason.SETTINGS_CONFLICT,assertThrows(KnowledgeDocumentException.class,()->service.reset(9)).reason());
            when(mapper.insert(any())).thenThrow(new DuplicateKeyException("private SQL"));
            var error=assertThrows(KnowledgeDocumentException.class,()->service.reset(0));
            assertEquals(KnowledgeDocumentException.Reason.SETTINGS_CONFLICT,error.reason());assertFalse(error.getMessage().contains("private"));
        }
    }
    @Test void missingIdentityCannotReadOrWriteTenantSettings(){
        var mapper=mock(KnowledgeSettingsMapper.class);var service=new KnowledgeSettingsService(mapper);
        try(var login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::isLogin).thenReturn(false);
            assertThrows(KnowledgeDocumentException.class,service::current);
            assertThrows(KnowledgeDocumentException.class,()->service.reset(0));verifyNoInteractions(mapper);
        }
    }
}
