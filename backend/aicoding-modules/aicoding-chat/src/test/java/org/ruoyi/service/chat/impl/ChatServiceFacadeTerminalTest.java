package org.ruoyi.service.chat.impl;

import cn.hutool.extra.spring.SpringUtil;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.sse.core.SseEmitterManager;
import org.ruoyi.common.sse.utils.SseMessageUtils;
import org.ruoyi.service.chat.IChatMessageService;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatServiceFacadeTerminalTest {
    @BeforeAll static void initializeSseUtility() {
        var context = new GenericApplicationContext();
        context.registerBean(SseEmitterManager.class, () -> mock(SseEmitterManager.class));
        context.refresh(); new SpringUtil().setApplicationContext(context);
    }

    @Test void timeoutPersistsPartialFailureAndSendsOneTerminalEvenWithLateCallbacks() {
        var messages = mock(IChatMessageService.class); var request = request(); var handler = handler(messages, request);
        try (var sse = mockStatic(SseMessageUtils.class)) {
            handler.onPartialResponse("已经生成的片段"); handler.onError(new TimeoutException());
            handler.onError(new IllegalStateException()); handler.onPartialResponse("迟到片段"); handler.onCompleteResponse(null);
            verify(messages, times(1)).saveChatMessage(eq(1L), eq(2L), contains("响应未完成"), eq("assistant"), eq("atlas-model"));
            sse.verify(() -> SseMessageUtils.sendErrorAndComplete("2", ChatServiceFacade.SAFE_CHAT_ERROR_MESSAGE), times(1));
            sse.verify(() -> SseMessageUtils.sendDone("2"), never());
            sse.verify(() -> SseMessageUtils.sendContent("2", "迟到片段"), never());
        }
    }

    @Test void successfulCompletionSavesAndClosesOnlyOnce() {
        var messages = mock(IChatMessageService.class); var handler = handler(messages, request());
        try (var sse = mockStatic(SseMessageUtils.class)) {
            handler.onPartialResponse("完整正文"); handler.onCompleteResponse(null); handler.onCompleteResponse(null); handler.onError(new TimeoutException());
            verify(messages, times(1)).saveChatMessage(1L, 2L, "完整正文", "assistant", "atlas-model");
            sse.verify(() -> SseMessageUtils.sendDone("2"), times(1));
            sse.verify(() -> SseMessageUtils.sendErrorAndComplete(anyString(), anyString()), never());
        }
    }

    @Test void emptyCompletionIsAnErrorInsteadOfSuccessfulEmptyManuscript() {
        var messages = mock(IChatMessageService.class); var handler = handler(messages, request());
        try (var sse = mockStatic(SseMessageUtils.class)) {
            handler.onCompleteResponse(null);
            verify(messages).saveChatMessage(1L, 2L, ChatServiceFacade.SAFE_CHAT_ERROR_MESSAGE, "assistant", "atlas-model");
            sse.verify(() -> SseMessageUtils.sendErrorAndComplete("2", ChatServiceFacade.SAFE_CHAT_ERROR_MESSAGE));
            sse.verify(() -> SseMessageUtils.sendDone("2"), never());
        }
    }

    @Test void questionSnapshotCapturesTokensAndSurvivesDuplicateCallbacks() throws Exception {
        var records = mock(org.ruoyi.service.chat.ChatQuestionService.class);
        var messages = mock(IChatMessageService.class);
        var facade = new ChatServiceFacade(null, null, null, null, null, messages, null, null, null, null, null, null, records, null);
        var q = new org.ruoyi.domain.entity.chat.ChatQuestion();
        q.setId(3L); q.setTenantId("000000"); q.setRetrievalStatus("no_match");
        var type = Class.forName(ChatServiceFacade.class.getName() + "$TraceRunHandle");
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var handle = constructor.newInstance(null, System.currentTimeMillis(), "2", "000000", q);
        StreamingChatResponseHandler handler = ReflectionTestUtils.invokeMethod(facade, "createModelChatResponseHandler", request(), handle, null);
        var response = dev.langchain4j.model.chat.response.ChatResponse.builder()
            .aiMessage(dev.langchain4j.data.message.AiMessage.from("完整正文"))
            .tokenUsage(new dev.langchain4j.model.output.TokenUsage(12, 8)).build();
        try (var sse = mockStatic(SseMessageUtils.class)) {
            handler.onPartialResponse("完整正文"); handler.onCompleteResponse(response);
            handler.onError(new TimeoutException()); handler.onCompleteResponse(response);
        }
        verify(records, times(1)).finish(q);
        org.junit.jupiter.api.Assertions.assertEquals("success", q.getAnswerStatus());
        org.junit.jupiter.api.Assertions.assertEquals("pending", q.getReviewStatus());
        org.junit.jupiter.api.Assertions.assertEquals(20L, q.getTotalTokens());
        org.junit.jupiter.api.Assertions.assertEquals("完整正文", q.getAnswer());
    }

    @Test void eachTurnUsesPersistedModeAndSingleSystemMessage() {
        var facade = new ChatServiceFacade(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var request = new ChatRequest(); request.setContent("继续"); request.setChatMode("learning");
        var agent = new org.ruoyi.domain.vo.agent.AgentVo(); agent.setSystemPrompt("遵循课程进度");
        for (int turn = 0; turn < 2; turn++) {
            java.util.List<dev.langchain4j.data.message.ChatMessage> messages = ReflectionTestUtils.invokeMethod(facade, "buildContextMessages", request, agent);
            org.junit.jupiter.api.Assertions.assertEquals(1, messages.stream().filter(m -> m instanceof dev.langchain4j.data.message.SystemMessage).count());
            var system = (dev.langchain4j.data.message.SystemMessage) messages.get(0);
            org.junit.jupiter.api.Assertions.assertTrue(system.text().contains("编程老师"));
            org.junit.jupiter.api.Assertions.assertTrue(system.text().contains("遵循课程进度"));
        }
        org.junit.jupiter.api.Assertions.assertThrows(org.ruoyi.common.core.exception.ServiceException.class,
            () -> org.ruoyi.service.chat.ChatModePolicy.normalize("spoofed"));
    }

    @Test void publicEntryReappliesSessionModeOnEachTurn() {
        var models = mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var factory = mock(org.ruoyi.factory.ChatServiceFactory.class);
        var sessions = mock(org.ruoyi.service.chat.ChatSessionOwnershipGuard.class);
        var events = mock(SseEmitterManager.class);
        var messages = mock(IChatMessageService.class);
        var records = mock(org.ruoyi.service.chat.ChatQuestionService.class);
        var config = new org.ruoyi.common.trace.config.TraceProperties(); config.setEnabled(false);
        var model = new org.ruoyi.common.chat.domain.vo.chat.ChatModelVo(); model.setProviderCode("test");
        when(models.selectModelByName("atlas-model")).thenReturn(model);
        var owned = new org.ruoyi.domain.vo.chat.ChatSessionVo(); owned.setChatMode("learning"); owned.setPromptSnapshot("快照指导"); owned.setLearningContext("章节材料");
        when(sessions.requireOwned(1L, 2L)).thenReturn(owned);
        when(events.connect("2")).thenAnswer(call -> new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
        var provider = mock(org.ruoyi.service.chat.AbstractChatService.class);
        var streaming = mock(dev.langchain4j.model.chat.StreamingChatModel.class);
        when(factory.getOriginalService("test")).thenReturn(provider);
        when(provider.buildStreamingChatModel(eq(model), any())).thenReturn(streaming);
        var facade = new ChatServiceFacade(models, factory, null, null, events, messages, sessions, null, null, null, null, config, records, null);
        java.util.Map<Object, dev.langchain4j.memory.chat.MessageWindowChatMemory> cache =
            (java.util.Map<Object, dev.langchain4j.memory.chat.MessageWindowChatMemory>) ReflectionTestUtils.getField(ChatServiceFacade.class, "memoryCache");
        cache.put(2L, dev.langchain4j.memory.chat.MessageWindowChatMemory.withMaxMessages(10));
        try (var login = mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class);
             var token = mockStatic(cn.dev33.satoken.stp.StpUtil.class);
             var tenant = mockStatic(org.ruoyi.common.tenant.helper.TenantHelper.class);
             var sse = mockStatic(SseMessageUtils.class)) {
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(1L);
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUsername).thenReturn("test");
            tenant.when(org.ruoyi.common.tenant.helper.TenantHelper::getTenantId).thenReturn("000000");
            for (int turn = 0; turn < 2; turn++) {
                var request = request(); request.setContent("继续"); request.setChatMode("debugging"); request.setPromptSnapshot("forged"); request.setLearningContext("forged");
                facade.sseChat(request);
                org.junit.jupiter.api.Assertions.assertEquals("learning", request.getChatMode());
                org.junit.jupiter.api.Assertions.assertEquals("快照指导", request.getPromptSnapshot());
                org.junit.jupiter.api.Assertions.assertEquals("章节材料", request.getLearningContext());
            }
            verify(streaming, times(2)).chat(argThat((java.util.List<dev.langchain4j.data.message.ChatMessage> list) ->
                list.get(0) instanceof dev.langchain4j.data.message.SystemMessage sm && sm.text().contains("编程老师") && sm.text().contains("快照指导") && sm.text().contains("章节材料") && !sm.text().contains("forged")), any(StreamingChatResponseHandler.class));
        } finally { cache.remove(2L); }
    }

    @Test void interruptedStreamPreservesPartialAnswerAndRejectsLateSuccess() throws Exception {
        var records = mock(org.ruoyi.service.chat.ChatQuestionService.class);
        var messages = mock(IChatMessageService.class);
        var facade = new ChatServiceFacade(null, null, null, null, null, messages, null, null, null, null, null,
            new org.ruoyi.common.trace.config.TraceProperties(), records, null);
        var q = new org.ruoyi.domain.entity.chat.ChatQuestion(); q.setRetrievalStatus("not_used");
        var type = Class.forName(ChatServiceFacade.class.getName() + "$TraceRunHandle");
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var handle = constructor.newInstance(null, System.currentTimeMillis(), "2", "000000", q);
        StreamingChatResponseHandler handler = ReflectionTestUtils.invokeMethod(facade, "createModelChatResponseHandler", request(), handle, null);
        try (var sse = mockStatic(SseMessageUtils.class)) {
            handler.onPartialResponse("已生成片段");
            ReflectionTestUtils.invokeMethod(facade, "finishTraceRun", handle, "error", new TimeoutException());
            handler.onPartialResponse("迟到片段"); handler.onCompleteResponse(null);
            sse.verify(() -> SseMessageUtils.sendDone("2"), never());
        }
        verify(records, times(1)).finish(q);
        verifyNoInteractions(messages);
        org.junit.jupiter.api.Assertions.assertEquals("已生成片段", q.getAnswer());
        org.junit.jupiter.api.Assertions.assertEquals("failed", q.getAnswerStatus());
        org.junit.jupiter.api.Assertions.assertEquals("pending", q.getReviewStatus());
    }

    private static ChatRequest request() {
        var request = new ChatRequest(); request.setUserId(1L); request.setSessionId(2L); request.setModel("atlas-model"); return request;
    }
    private static StreamingChatResponseHandler handler(IChatMessageService messages, ChatRequest request) {
        var facade = new ChatServiceFacade(null, null, null, null, null, messages, null, null, null, null, null, null, null, null);
        return ReflectionTestUtils.invokeMethod(facade, "createModelChatResponseHandler", request, null, null);
    }
}
