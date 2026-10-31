package org.ruoyi.websocket.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.service.knowledge.KnowledgeBaseAccessService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.AbstractChatService;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.chat.IChatMessageService;
import org.ruoyi.service.knowledge.IKnowledgeInfoService;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class MpChatKnowledgeAccessTest {
    private final ObjectMapper json = new ObjectMapper();
    private final IChatModelService models = mock(IChatModelService.class);
    private final ChatServiceFactory factory = mock(ChatServiceFactory.class);
    private final AbstractChatService provider = mock(AbstractChatService.class);
    private final StreamingChatModel streaming = mock(StreamingChatModel.class);
    private final IAgentService agents = mock(IAgentService.class);
    private final IKnowledgeInfoService unguarded = mock(IKnowledgeInfoService.class);
    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final IChatMessageService messages = mock(IChatMessageService.class);
    private final ChatSessionOwnershipGuard sessions = mock(ChatSessionOwnershipGuard.class);
    private final WebSocketSession socket = mock(WebSocketSession.class);
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    private final ChatSessionVo owned = new ChatSessionVo();
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Map<Long, KnowledgeInfoVo> bases = new HashMap<>();
    private final KnowledgeInfoMapper knowledgeMapper = mock(KnowledgeInfoMapper.class);
    private final Map<Long, Integer> lookupCounts = new HashMap<>();
    private boolean revokeAfterFirstLookup;
    private MpChatWebSocketHandler handler;

    @BeforeEach void setup() throws Exception {
        var user = new LoginUser(); user.setUserId(7L); user.setTenantId("000007");
        attributes.put(MpChatHandshakeInterceptor.USER_ID_KEY, 7L);
        attributes.put(MpChatHandshakeInterceptor.LOGIN_USER_KEY, user);
        when(socket.getAttributes()).thenReturn(attributes); when(socket.isOpen()).thenReturn(true);
        doAnswer(call -> { sent.add(((TextMessage) call.getArgument(0)).getPayload()); return null; }).when(socket).sendMessage(any());
        owned.setUserId(7L); when(sessions.requireOwned(7L, 2L, "000007")).thenReturn(owned);
        var model = new ChatModelVo(); model.setModelName("model"); model.setProviderCode("test");
        when(models.selectModelByName(anyString())).thenReturn(model);
        when(factory.getOriginalService("test")).thenReturn(provider);
        when(provider.buildStreamingChatModel(any(), any())).thenReturn(streaming);
        when(retrieval.retrieve(any())).thenReturn(List.of());
        when(unguarded.queryById(anyLong())).thenAnswer(call -> bases.get(call.getArgument(0)));
        doAnswer(call -> {
            StreamingChatResponseHandler callback = call.getArgument(1);
            callback.onPartialResponse("answer"); callback.onCompleteResponse(null); finished.countDown(); return null;
        }).when(streaming).chat(anyString(), any(StreamingChatResponseHandler.class));
        when(knowledgeMapper.selectVoOne(any(Wrapper.class))).thenAnswer(call -> {
            QueryWrapper<KnowledgeInfo> query = call.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.matches("(?s).*AND \\(share = .* OR user_id = .*\\).*"), sql);
            assertEquals("000007", bound(query, "tenant_id"));
            assertEquals(7L, ((Number) bound(query, "user_id")).longValue());
            Long id = ((Number) bound(query, "id")).longValue();
            int count = lookupCounts.merge(id, 1, Integer::sum);
            if (revokeAfterFirstLookup && count > 1) return null;
            var info = bases.get(id);
            if (info == null || id == 44L) return null; // This public row belongs to another tenant.
            return info.getShare() == 1L || info.getUserId() == 7L ? info : null;
        });
        handler = new MpChatWebSocketHandler(factory, models, agents, unguarded, retrieval, messages, sessions, json,
            new KnowledgeBaseAccessService(knowledgeMapper, mock(KnowledgeAttachMapper.class)));
    }

    @Test void missingHandshakePrincipalCannotUsePayloadIdentityOrLegacyUserIdAttribute() throws Exception {
        attributes.remove(MpChatHandshakeInterceptor.LOGIN_USER_KEY);
        receive(Map.of("userId", 7, "tenantId", "000007"));
        assertRejectedBeforeModel(); verifyNoInteractions(sessions);
    }

    @Test void inconsistentOrTenantlessHandshakeIdentityFailsClosed() throws Exception {
        ((LoginUser) attributes.get(MpChatHandshakeInterceptor.LOGIN_USER_KEY)).setTenantId(null);
        receive(Map.of()); assertRejectedBeforeModel();
        verifyNoInteractions(sessions);
    }

    @Test void anotherOwnersPrivateBaseCannotReachModelOrRetrieval() throws Exception {
        base(43L, "private-other-owner");
        receive(Map.of("knowledgeId", "43", "userId", 99, "tenantId", "other"));
        assertRejectedBeforeModel(); verifyNoInteractions(unguarded, retrieval, factory, messages);
    }

    @Test void evenPublicKnowledgeInAnotherTenantIsUnavailable() throws Exception {
        base(44L, "other-tenant-public");
        receive(Map.of("knowledgeId", "44", "tenantId", "000044"));
        assertRejectedBeforeModel(); verifyNoInteractions(unguarded, retrieval);
    }

    @Test void savedBindingRestoresWhenClientOmitsSelection() throws Exception {
        owned.setKnowledgeId("42"); base(42L, "owned");
        receive(Map.of()); awaitSuccess();
        var request = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(provider).buildStreamingChatModel(any(), request.capture());
        assertEquals("42", request.getValue().getKnowledgeId());
        verify(retrieval).retrieve(argThat(query -> "42".equals(query.getKid())));
        verifyNoInteractions(unguarded);
    }

    @Test void differentExplicitSelectionCannotOverrideSavedBinding() throws Exception {
        owned.setKnowledgeId("42"); base(43L, "private-other-owner");
        receive(Map.of("knowledgeId", "43")); assertRejectedBeforeModel();
        verifyNoInteractions(unguarded, retrieval);
    }

    @Test void publicBaseIsAllowedUsingTrustedTenantWithoutHttpLoginContext() throws Exception {
        base(45L, "public"); receive(Map.of("knowledgeId", "45")); awaitSuccess();
        verify(retrieval).retrieve(argThat(query -> "45".equals(query.getKid())));
        verifyNoInteractions(unguarded);
        assertTrue(sent.stream().anyMatch(value -> value.equals("{\"content\":\"answer\"}")));
        assertEquals(1, sent.stream().filter("[DONE]"::equals).count());
    }

    @Test void ownPrivateBaseIsAllowedWithoutOpeningAnUnrestrictedLookup() throws Exception {
        base(42L, "owned"); receive(Map.of("knowledgeId", "42")); awaitSuccess();
        verify(retrieval).retrieve(argThat(query -> "42".equals(query.getKid())));
        verifyNoInteractions(unguarded);
    }

    @Test void agentConfigurationDoesNotAuthorizeAnAdditionalPrivateRequestSource() throws Exception {
        var agent = new AgentVo(); agent.setKnowledgeIds(List.of(46L));
        when(agents.queryById(9L)).thenReturn(agent);
        base(46L, "agent-configured"); base(43L, "private-other-owner");
        receive(Map.of("agentId", "9", "knowledgeId", "43")); assertRejectedBeforeModel();
        verifyNoInteractions(unguarded, retrieval);
    }

    @Test void malformedKnowledgeIdsAreRejectedBeforeModelSelection() throws Exception {
        for (String id : List.of("bad", "9223372036854775808")) receive(Map.of("knowledgeId", id));
        assertRejectedBeforeModel(); verifyNoInteractions(unguarded, retrieval);
    }

    @Test void permissionRevocationAtRetrievalCannotUseOpenLookupOrFallBackToAPlainAnswer() throws Exception {
        base(42L, "owned"); revokeAfterFirstLookup = true;
        receive(Map.of("knowledgeId", "42"));
        assertTrue(sent.stream().anyMatch(value -> value.contains("错误:")));
        verifyNoInteractions(unguarded, retrieval, factory, messages);
        assertEquals(2, lookupCounts.get(42L));
    }

    @Test void agentOwnedSourceRemainsAvailableWithoutAddingAUserSelection() throws Exception {
        var agent = new AgentVo(); agent.setKnowledgeIds(List.of(46L));
        when(agents.queryById(9L)).thenReturn(agent); base(46L, "agent-configured");
        owned.setKnowledgeId("43"); receive(Map.of("agentId", "9")); awaitSuccess();
        verify(unguarded).queryById(46L); verifyNoInteractions(knowledgeMapper);
        verify(retrieval).retrieve(argThat(query -> "46".equals(query.getKid())));
    }

    @Test void partialOutputThenFailureKeepsTheExistingDoneProtocol() throws Exception {
        doAnswer(call -> {
            StreamingChatResponseHandler callback = call.getArgument(1);
            callback.onPartialResponse("partial"); callback.onError(new java.util.concurrent.TimeoutException());
            finished.countDown(); return null;
        }).when(streaming).chat(anyString(), any(StreamingChatResponseHandler.class));
        receive(Map.of()); awaitSuccess();
        assertEquals(List.of("{\"content\":\"partial\"}", "[DONE]"), sent);
    }

    @Test void teachingIncludesTheCurrentCourseMaterialsResolvedForTrustedTenant() throws Exception {
        owned.setChatMode("learning"); owned.setLearningContext("最新课程资料：示例项目叫青竹课堂");
        receive(Map.of()); awaitSuccess();
        verify(streaming).chat(contains("示例项目叫青竹课堂"), any(StreamingChatResponseHandler.class));
        verify(sessions).requireOwned(7L, 2L, "000007");
    }

    @Test void inaccessibleTeachingMaterialsRejectBeforeModelSelection() throws Exception {
        when(sessions.requireOwned(7L, 2L, "000007")).thenThrow(new org.ruoyi.common.core.exception.ServiceException("会话不存在或无权访问"));
        receive(Map.of()); assertRejectedBeforeModel();
        verifyNoInteractions(factory, messages);
    }

    @Test void disabledRetrievalMustNotFallBackToPlainAnswer() throws Exception {
        base(42L, "owned");
        when(retrieval.retrieve(any())).thenThrow(new org.ruoyi.service.knowledge.KnowledgeBaseDisabledException());
        receive(Map.of("knowledgeId", "42"));
        for (int attempt = 0; attempt < 100 && sent.isEmpty(); attempt++) Thread.sleep(20);
        assertTrue(sent.stream().anyMatch(value -> value.contains("知识库已停用")), sent.toString());
        assertFalse(sent.stream().anyMatch(value -> value.contains("\"content\"")), sent.toString());
        verify(streaming, never()).chat(anyString(), any(StreamingChatResponseHandler.class));
    }

    @Test void disabledSavedBindingRejectsBeforeModelSelection() throws Exception {
        owned.setKnowledgeId("42"); base(42L, "owned"); bases.get(42L).setStatus("1");
        receive(Map.of()); assertRejectedBeforeModel();
        assertTrue(sent.stream().anyMatch(value -> value.contains("知识库已停用")), sent.toString());
    }

    private void base(Long id, String name) {
        var info = new KnowledgeInfoVo(); info.setId(id); info.setStatus("0"); info.setName(name); info.setEmbeddingModel("embedding");
        info.setUserId(id == 42L ? 7L : 99L); info.setShare(id == 44L || id == 45L ? 1L : 0L);
        bases.put(id, info);
    }
    private Object bound(QueryWrapper<KnowledgeInfo> query, String column) {
        var matcher = java.util.regex.Pattern.compile("\\b" + column + " = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
            .matcher(query.getSqlSegment());
        assertTrue(matcher.find(), query.getSqlSegment());
        return query.getParamNameValuePairs().get(matcher.group(1));
    }
    private void receive(Map<String, Object> extras) throws Exception {
        Map<String, Object> payload = new HashMap<>(Map.of("sessionId", "2", "content", "question", "model", "model"));
        payload.putAll(extras); handler.handleTextMessage(socket, new TextMessage(json.writeValueAsString(payload)));
    }
    private void assertRejectedBeforeModel() {
        assertTrue(sent.stream().anyMatch(value -> value.contains("错误:")), sent.toString());
        assertFalse(sent.stream().anyMatch(value -> value.contains("\"content\"")), sent.toString());
        verifyNoInteractions(models);
    }
    private void awaitSuccess() throws Exception { assertTrue(finished.await(2, TimeUnit.SECONDS)); }
}
