package org.ruoyi.service.chat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.chat.ChatQuestion;
import org.ruoyi.mapper.chat.ChatQuestionMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class ChatQuestionServiceTest {
    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "question-test"), ChatQuestion.class);
    }
    private final ChatQuestionMapper mapper = mock(ChatQuestionMapper.class);
    private final ChatQuestionService service = new ChatQuestionService(mapper, null, null);
    private ChatQuestion record() {
        var q = new ChatQuestion(); q.setId(10L); q.setUserId(1L); q.setAnswerStatus("success");
        q.setVersion(2); q.setIssueType("none"); q.setTenantId("000000"); return q;
    }
    @Test void resolvedReviewRequiresNoteAndRejectsConcurrentChange() {
        when(mapper.selectById(10L)).thenReturn(record());
        assertThrows(ServiceException.class, () -> service.review(10L, "resolved", " ", 2));
        assertThrows(ServiceException.class, () -> service.review(10L, "unknown", "", 2));
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.review(10L, "resolved", "修正文档", 2));
            when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
                Wrapper<?> w = call.getArgument(1);
                assertTrue(w.getSqlSegment().contains("version"));
                return 1;
            });
            assertDoesNotThrow(() -> service.review(10L, "resolved", "修正文档", 2));
        }
    }
    @Test void cannotReviewRunningAnswer() {
        var q = record(); q.setAnswerStatus("running"); when(mapper.selectById(10L)).thenReturn(q);
        assertThrows(ServiceException.class, () -> service.review(10L, "ignored", "", 2));
        verify(mapper, never()).update(any(), any(Wrapper.class));
    }
    @Test void feedbackIsOwnerOnlyAndIdempotent() {
        var q = record(); when(mapper.selectById(10L)).thenReturn(q);
        assertThrows(ServiceException.class, () -> service.feedback(10L, 99L));
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) call.getArgument(1);
            assertTrue(wrapper.getParamNameValuePairs().containsValue("dissatisfied"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("pending"));
            return 1;
        });
        service.feedback(10L, 1L);
        q.setIssueType("dissatisfied"); service.feedback(10L, 1L);
        verify(mapper, times(1)).update(isNull(), any(Wrapper.class));
    }
    @Test void callbackFinishAlwaysIncludesTenantAndRunningPredicates() {
        var q = record(); q.setAnswerStatus("failed");
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(() -> TenantHelper.ignore(any(Runnable.class))).thenAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; });
            service.finish(q);
            verify(mapper).update(eq(q), argThat((Wrapper<ChatQuestion> w) -> w.getSqlSegment().contains("tenant_id") && w.getSqlSegment().contains("answer_status")));
        }
        q.setTenantId(null); assertThrows(IllegalArgumentException.class, () -> service.finish(q));
    }
}
