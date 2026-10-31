package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class KnowledgeStatusServiceTest {
    private final KnowledgeInfoMapper mapper=mock(KnowledgeInfoMapper.class);
    private final KnowledgeStatusService service=new KnowledgeStatusService(mapper);

    @Test void toggleScopesTenantAndUpdatesOnlyStatusAndAuditFields() {
        try(var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)) {
            login.when(LoginHelper::isLogin).thenReturn(true);login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000007");tenant.when(TenantHelper::getTenantId).thenReturn(null);
            when(mapper.update(isNull(),any(Wrapper.class))).thenAnswer(call->{
                UpdateWrapper<?> query=call.getArgument(1);
                assertTrue(query.getSqlSegment().contains("tenant_id"));
                assertTrue(query.getParamNameValuePairs().containsValue("000007"));
                assertEquals(3,query.getSqlSet().split(",").length);
                assertTrue(query.getSqlSet().contains("status="));
                assertFalse(query.getSqlSet().contains("name="));return 1;
            });
            service.changeStatus(42L,"1");service.changeStatus(42L,"0");
            when(mapper.update(isNull(),any(Wrapper.class))).thenReturn(0);
            assertThrows(ServiceException.class,()->service.changeStatus(99L,"1"));
        }
    }

    @Test void invalidStatusAndMissingIdentityNeverWrite() {
        for(String value:new String[]{null,"","2","enabled","1 OR 1=1"})assertThrows(ServiceException.class,()->service.changeStatus(42L,value));
        try(var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)) {
            assertThrows(ServiceException.class,()->service.changeStatus(42L,"1"));
            login.when(LoginHelper::isLogin).thenReturn(true);login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class,()->service.changeStatus(42L,"1"));
        }
        verifyNoInteractions(mapper);
    }

    @Test void statusIsReadFreshAndInvalidKnowledgeNeverQueriesDatabase() {
        for(String value:new String[]{null,"","bad","0","9223372036854775808"})assertThrows(ServiceException.class,()->service.requireEnabled(value));
        verifyNoInteractions(mapper);
        var info=new KnowledgeInfo();info.setStatus("0");when(mapper.selectById(42L)).thenReturn(info);
        service.requireEnabled("42");info.setStatus("1");
        assertEquals("知识库已停用",assertThrows(KnowledgeBaseDisabledException.class,()->service.requireEnabled("42")).getMessage());
        info.setStatus("0");service.requireEnabled("42");
        when(mapper.selectById(42L)).thenReturn(null);assertThrows(ServiceException.class,()->service.requireEnabled("42"));
    }
}
