package org.ruoyi.service.chat;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.*;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.domain.bo.chat.ChatSessionBo;
import org.ruoyi.domain.entity.chat.*;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.service.chat.impl.ChatSessionServiceImpl;
import org.springframework.context.support.GenericApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatPromptSnapshotTest {
    @BeforeAll static void setup() {
        var context = new GenericApplicationContext();
        context.registerBean(Converter.class, () -> mock(Converter.class));
        context.refresh(); new SpringUtil().setApplicationContext(context);
    }
    @Test void templateContentsAreCopiedOnceAndCannotBeChangedOnExistingSession() {
        var mapper = mock(ChatSessionMapper.class); var prompts = mock(ChatPromptService.class);
        var service = new ChatSessionServiceImpl(mapper, prompts, mock(org.ruoyi.service.knowledge.KnowledgeBaseAccessService.class));
        var p = new ChatPrompt(); p.setId(12L); p.setName("排错指导"); p.setContent("原始指导"); p.setVersion(3);
        when(prompts.resolve(12L, "debugging")).thenReturn(p);
        var bo = new ChatSessionBo(); bo.setChatMode("debugging"); bo.setPromptId(12L);
        var stored = new ChatSession(); stored.setChatMode("debugging");
        when(mapper.insert(any(ChatSession.class))).thenAnswer(call -> {stored.setId(20L); return 1;});
        try (var converter = mockStatic(MapstructUtils.class)) {
            converter.when(() -> MapstructUtils.convert(bo, ChatSession.class)).thenReturn(stored);
            assertTrue(service.insertByBo(bo)); assertEquals(20L, bo.getId());
        }
        p.setContent("更新指导"); p.setVersion(4);
        assertEquals("原始指导", stored.getPromptSnapshot()); assertEquals(3, stored.getPromptVersion());
        when(mapper.selectById(20L)).thenReturn(stored);
        var edit = new ChatSessionBo(); edit.setId(20L); edit.setChatMode("learning");
        assertThrows(ServiceException.class, () -> service.updateByBo(edit));
        edit.setChatMode("debugging"); edit.setPromptId(99L);
        assertThrows(ServiceException.class, () -> service.updateByBo(edit));
        verify(mapper, never()).updateById(any(ChatSession.class));
    }
    @Test void clientCannotInjectServerOwnedTemplateAndLearningMaterial() throws Exception {
        var json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var request = json.readValue("{\"promptSnapshot\":\"forged\",\"learningContext\":\"forged\"}", ChatRequest.class);
        assertNull(request.getPromptSnapshot()); assertNull(request.getLearningContext());
        var vo = new ChatSessionVo(); vo.setPromptSnapshot("private instructions"); vo.setLearningContext("private lesson");
        String response = json.writeValueAsString(vo);
        assertFalse(response.contains("promptSnapshot")); assertFalse(response.contains("learningContext"));
    }
}
