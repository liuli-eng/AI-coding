package org.ruoyi.service.chat.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.sse.core.SseEmitterManager;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.knowledge.KnowledgeBaseAccessService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatKnowledgeBindingTest {
    @Test void savedBindingRestoresRetrievalAndExplicitMismatchFailsBeforeOpeningSse() {
        var access = access(); var sessions = mock(ChatSessionOwnershipGuard.class); var events = mock(SseEmitterManager.class);
        var owned = new ChatSessionVo(); owned.setKnowledgeId("42");
        when(sessions.requireOwned(7L, 2L)).thenReturn(owned);
        var facade = new ChatServiceFacade(null, null, null, null, events, null, sessions, null, null, null, null, null, null, access);
        try (var login = mockStatic(LoginHelper.class); var token = mockStatic(StpUtil.class); var tenant = mockStatic(TenantHelper.class)) {
            identity(login, tenant);
            var request = request(); request.setKnowledgeId("43");
            assertThrows(ServiceException.class, () -> facade.sseChat(request));
            verifyNoInteractions(events);
            var restored = request();
            // Missing model is rejected after the persisted source has been restored and authorized.
            assertThrows(IllegalArgumentException.class, () -> facade.sseChat(restored));
            assertEquals("42", restored.getKnowledgeId());
            verifyNoInteractions(events);
        }
    }

    @Test void unavailableOrMalformedLegacySelectionFailsBeforeOpeningSse() {
        var mapper = mock(KnowledgeInfoMapper.class); var access = new KnowledgeBaseAccessService(mapper, mock(KnowledgeAttachMapper.class));
        var sessions = mock(ChatSessionOwnershipGuard.class); var events = mock(SseEmitterManager.class);
        when(sessions.requireOwned(7L, 2L)).thenReturn(new ChatSessionVo());
        var facade = new ChatServiceFacade(null, null, null, null, events, null, sessions, null, null, null, null, null, null, access);
        try (var login = mockStatic(LoginHelper.class); var token = mockStatic(StpUtil.class); var tenant = mockStatic(TenantHelper.class)) {
            identity(login, tenant);
            for (String id : List.of("42", "bad", "9223372036854775808")) {
                var request = request(); request.setKnowledgeId(id);
                assertThrows(ServiceException.class, () -> facade.sseChat(request));
            }
            verifyNoInteractions(events);
        }
    }

    @Test void legacyUnboundSelectionIsAllowedOnlyAfterAccessCheck() {
        try (var login = mockStatic(LoginHelper.class); var tenant = mockStatic(TenantHelper.class)) {
            identity(login, tenant);
            assertEquals("42", access().resolveForChat(null, "42"));
            assertEquals("42", access().resolveForChat("42", null));
            assertEquals("42", access().resolveForChat("42", "42"));
        }
    }

    @Test void agentKnowledgeCollectionKeepsItsExistingPriority() {
        var facade = new ChatServiceFacade(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var request = request(); request.setKnowledgeId("99");
        var agent = new AgentVo(); agent.setKnowledgeIds(List.of(10L, 11L));
        assertEquals(List.of(10L, 11L), ReflectionTestUtils.invokeMethod(facade, "collectKnowledgeIds", request, agent));
    }

    @Test void revokedKnowledgeAccessAtRetrievalFailsInsteadOfSilentlySendingAnUngroundedAnswer() {
        var guard = mock(KnowledgeBaseAccessService.class);
        when(guard.requireAccessible("42")).thenThrow(new ServiceException("知识库不可用"));
        var infos = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var facade = new ChatServiceFacade(null, null, infos, null, null, null, null, null, null, null, null, null, null, guard);
        var request = request(); request.setKnowledgeId("42"); request.setContent("私有材料问题");
        assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(facade, "augmentAgentInput", request, null, null));
        verifyNoInteractions(infos);
    }

    @Test void workflowKeepsItsRoutingEvenWithAnOrdinaryKnowledgeBinding() {
        var sessions = mock(ChatSessionOwnershipGuard.class); var events = mock(SseEmitterManager.class);
        var messages = mock(org.ruoyi.service.chat.IChatMessageService.class);
        var workflows = mock(org.ruoyi.common.chat.service.workFlow.IWorkFlowStarterService.class);
        var guard = mock(KnowledgeBaseAccessService.class);
        var owned = new ChatSessionVo(); owned.setKnowledgeId("unavailable-ordinary-source");
        when(sessions.requireOwned(7L, 2L)).thenReturn(owned);
        var facade = new ChatServiceFacade(null, null, null, null, events, messages, sessions, workflows, null, null, null, null, null, guard);
        var request = request(); request.setEnableWorkFlow(true);
        var runner = new org.ruoyi.common.chat.domain.dto.request.WorkFlowRunner(); runner.setUuid("workflow-1");
        request.setWorkFlowRunner(runner);
        try (var login = mockStatic(LoginHelper.class); var token = mockStatic(StpUtil.class);
             var context = mockStatic(org.ruoyi.common.chat.base.ThreadContext.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            facade.sseChat(request);
            verify(workflows).streaming(any(), eq("workflow-1"), eq(List.of()), eq(2L));
            verifyNoInteractions(guard, events);
        }
    }

    @Test void agentRetrieverKeepsUsingAgentConfigurationWithoutOrdinaryGuard() {
        var guard = mock(KnowledgeBaseAccessService.class);
        var infos = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var models = mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var kb = new KnowledgeInfoVo(); kb.setEmbeddingModel("agent-embedding");
        when(infos.queryById(10L)).thenReturn(kb);
        var facade = new ChatServiceFacade(models, null, infos, null, null, null, null, null, null, null, null, null, null, guard);
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(facade, "buildMultiKnowledgeAugmentor", List.of(10L), null, false));
        verify(infos).queryById(10L); verifyNoInteractions(guard);
    }

    @Test void agentWithoutBoundKnowledgeCannotUseAPrivateUserSuppliedFallbackBeforeSse() {
        var sessions = mock(ChatSessionOwnershipGuard.class); var events = mock(SseEmitterManager.class);
        var agents = mock(org.ruoyi.service.agent.IAgentService.class);
        var guard = mock(KnowledgeBaseAccessService.class);
        when(guard.requireAccessible("43")).thenThrow(new ServiceException("知识库不可用"));
        when(sessions.requireOwned(7L, 2L)).thenReturn(new ChatSessionVo());
        var agent = new AgentVo(); agent.setStatus("0"); agent.setKnowledgeIds(List.of());
        when(agents.queryById(9L)).thenReturn(agent);
        var facade = new ChatServiceFacade(null, null, null, null, events, null, sessions, null, agents, null, null, null, null, guard);
        var request = request(); request.setAgentId(9L); request.setKnowledgeId("43");
        try (var login = mockStatic(LoginHelper.class); var token = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> facade.sseChat(request));
            verify(guard).requireAccessible("43"); verifyNoInteractions(events);
        }
    }

    @Test void agentFallbackRetrievalRechecksTheUserSuppliedSource() {
        var guard = mock(KnowledgeBaseAccessService.class);
        when(guard.requireAccessible("43")).thenThrow(new ServiceException("知识库不可用"));
        var infos = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var facade = new ChatServiceFacade(null, null, infos, null, null, null, null, null, null, null, null, null, null, guard);
        var request = request(); request.setKnowledgeId("43"); request.setContent("问题");
        var agent = new AgentVo(); agent.setKnowledgeIds(List.of());
        assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(facade, "augmentAgentInput", request, agent, null));
        verify(guard).requireAccessible("43"); verifyNoInteractions(infos);
    }

    @Test void disabledKnowledgeMustNotFallBackToPlainAnswer() {
        var guard = mock(KnowledgeBaseAccessService.class);
        when(guard.requireAccessible("42")).thenThrow(new org.ruoyi.service.knowledge.KnowledgeBaseDisabledException());
        var facade = new ChatServiceFacade(null, null, null, null, null, null, null, null, null, null, null, null, null, guard);
        var request = request(); request.setKnowledgeId("42"); request.setContent("资料问题");
        assertThrows(org.ruoyi.service.knowledge.KnowledgeBaseDisabledException.class,
            () -> ReflectionTestUtils.invokeMethod(facade, "augmentAgentInput", request, null, null));
    }

    private static KnowledgeBaseAccessService access() {
        var mapper = mock(KnowledgeInfoMapper.class); var vo = new KnowledgeInfoVo(); vo.setId(42L); vo.setStatus("0");
        when(mapper.selectVoOne(any())).thenReturn(vo);
        return new KnowledgeBaseAccessService(mapper, mock(KnowledgeAttachMapper.class));
    }
    private static ChatRequest request() { var request = new ChatRequest(); request.setSessionId(2L); return request; }
    private static void identity(org.mockito.MockedStatic<LoginHelper> login, org.mockito.MockedStatic<TenantHelper> tenant) {
        login.when(LoginHelper::isLogin).thenReturn(true); login.when(LoginHelper::getUserId).thenReturn(7L);
        tenant.when(TenantHelper::getTenantId).thenReturn("000007");
    }
}
