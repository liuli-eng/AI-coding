package org.ruoyi.controller.knowledge;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.web.handler.GlobalExceptionHandler;
import org.ruoyi.service.knowledge.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("dev")
class KnowledgeStatusHttpTest {
    @Test void statusEndpointValidatesBodyAndKeepsExistingEditPermission() throws Exception {
        var service=mock(KnowledgeStatusService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new KnowledgeStatusController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/system/info/42/status").contentType("application/json").content("{\"status\":\"1\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).changeStatus(42L,"1");clearInvocations(service);
        for(String body:new String[]{"{}","{\"status\":\"2\"}"})mvc.perform(put("/system/info/42/status").contentType("application/json").content(body)).andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
        assertArrayEquals(new String[]{"system:info:edit"},KnowledgeStatusController.class.getMethod("change",Long.class,KnowledgeStatusController.Status.class).getAnnotation(SaCheckPermission.class).value());
    }
    @Test void onlyFixedDisabledReasonEscapesGlobalRedaction() throws Exception {
        var access=mock(KnowledgeBaseAccessService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new KnowledgeBaseController(access)).setControllerAdvice(new KnowledgeStatusExceptionHandler(),new GlobalExceptionHandler()).build();
        when(access.getSummary("42")).thenThrow(new KnowledgeBaseDisabledException());
        mvc.perform(get("/knowledge/bases/42")).andExpect(jsonPath("$.msg").value("知识库已停用"));
        doThrow(new ServiceException("private-storage-secret")).when(access).getSummary("42");
        mvc.perform(get("/knowledge/bases/42")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-storage-secret"))));
    }
}
