package org.ruoyi.service.chat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.chat.ChatPromptSave;
import org.ruoyi.domain.entity.chat.ChatPrompt;
import org.ruoyi.mapper.chat.ChatPromptMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatPromptServiceTest {
    @BeforeAll static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "prompt-test"), ChatPrompt.class);
    }
    private final ChatPromptMapper mapper = mock(ChatPromptMapper.class);
    private final ChatPromptService service = new ChatPromptService(mapper);
    private ChatPrompt prompt() {
        var p = new ChatPrompt(); p.setId(12L); p.setName("排错指导"); p.setContent("先定位根因");
        p.setChatMode("debugging"); p.setStatus("enabled"); p.setVersion(3); return p;
    }
    @Test void disabledAndMismatchedTemplatesCannotCreateSessions() {
        var p = prompt(); when(mapper.selectById(12L)).thenReturn(p);
        assertNull(service.resolve(null, "learning"));
        assertSame(p, service.resolve(12L, "debugging"));
        assertThrows(ServiceException.class, () -> service.resolve(12L, "learning"));
        p.setStatus("disabled");
        assertThrows(ServiceException.class, () -> service.resolve(12L, "debugging"));
        assertThrows(ServiceException.class, () -> service.resolve(99L, "debugging"));
    }
    @Test void editingAndDeletingUseVersionPredicateAndRejectConflicts() {
        when(mapper.selectById(12L)).thenReturn(prompt());
        var body = new ChatPromptSave("排错指导", "", "debugging", "先定位根因", "enabled", 3);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.save(12L, body));
        when(mapper.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> w = call.getArgument(1);
            assertTrue(w.getSqlSegment().contains("version")); return 1;
        });
        assertEquals(12L, service.save(12L, body));
        when(mapper.delete(any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.remove(12L, 3));
        when(mapper.delete(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> w = call.getArgument(0); assertTrue(w.getSqlSegment().contains("version")); return 1;
        });
        assertDoesNotThrow(() -> service.remove(12L, 3));
    }
    @Test void optionsDoNotExposePromptBody() {
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> w = call.getArgument(0);
            assertFalse(w.getSqlSelect().contains("content"));
            assertTrue(w.getSqlSegment().contains("status")); return java.util.List.of();
        });
        service.options("debugging");
    }
}
