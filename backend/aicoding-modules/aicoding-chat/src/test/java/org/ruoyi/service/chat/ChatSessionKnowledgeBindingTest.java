package org.ruoyi.service.chat;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.domain.bo.chat.ChatSessionBo;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.service.chat.impl.ChatSessionServiceImpl;
import org.ruoyi.service.knowledge.KnowledgeBaseAccessService;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatSessionKnowledgeBindingTest {
    private final ObjectMapper json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @BeforeAll static void initializeConverter() {
        var context = new GenericApplicationContext();
        context.registerBean(Converter.class, () -> mock(Converter.class));
        context.refresh(); new SpringUtil().setApplicationContext(context);
    }

    @Test void knowledgeBindingIsRepresentedAsAStringOnBoEntityAndResponse() throws Exception {
        String request = "{\"knowledgeId\":\"2033198818050781187\"}";
        for (Class<?> type : new Class<?>[]{ChatSessionBo.class, ChatSession.class, ChatSessionVo.class}) {
            var restored = json.valueToTree(json.readValue(request, type));
            assertEquals("2033198818050781187", restored.path("knowledgeId").asText(), type.getSimpleName());
        }
    }

    @Test void malformedBindingIsRejectedBeforeSessionInsert() throws Exception {
        var mapper = mock(ChatSessionMapper.class);
        var service = new ChatSessionServiceImpl(mapper, mock(ChatPromptService.class), access());
        var bo = json.readValue("{\"knowledgeId\":\"not-an-id\"}", ChatSessionBo.class);
        var stored = new ChatSession();
        try (var conversion = mockStatic(MapstructUtils.class)) {
            conversion.when(() -> MapstructUtils.convert(bo, ChatSession.class)).thenReturn(stored);
            assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        }
        verify(mapper, never()).insert(any(ChatSession.class));
    }
    @Test void createPersistsBindingAndGetAndListRestoreItThroughRealGeneratedMappings() {
        var mapper = mock(ChatSessionMapper.class);
        var access = mock(KnowledgeBaseAccessService.class);
        var service = new ChatSessionServiceImpl(mapper, mock(ChatPromptService.class), access);
        var bo = new ChatSessionBo(); bo.setKnowledgeId("42");
        var entityMapping = new org.ruoyi.domain.bo.chat.ChatSessionBoToChatSessionMapperImpl();
        var viewMapping = new org.ruoyi.domain.entity.chat.ChatSessionToChatSessionVoMapperImpl();
        var stored = entityMapping.convert(bo);
        when(mapper.insert(any(ChatSession.class))).thenAnswer(call -> {
            ChatSession entity = call.getArgument(0); assertEquals("42", entity.getKnowledgeId());
            entity.setId(21L); return 1;
        });
        try (var conversion = mockStatic(MapstructUtils.class)) {
            conversion.when(() -> MapstructUtils.convert(bo, ChatSession.class)).thenReturn(stored);
            assertTrue(service.insertByBo(bo));
        }
        verify(access).requireAccessible("42");
        var restored = viewMapping.convert(stored);
        when(mapper.selectVoById(21L)).thenReturn(restored);
        when(mapper.selectVoList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(restored));
        assertEquals("42", service.queryById(21L).getKnowledgeId());
        // Initialize lambda metadata without a database for the session list query.
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test"), ChatSession.class);
        assertEquals("42", service.queryList(new ChatSessionBo()).get(0).getKnowledgeId());
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<ChatSessionVo>(1, 12, 1);
        page.setRecords(java.util.List.of(restored));
        when(mapper.selectVoPage(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(page);
        assertEquals("42", service.queryPageList(new ChatSessionBo(), new org.ruoyi.common.mybatis.core.page.PageQuery(12, 1)).getRows().get(0).getKnowledgeId());
        assertEquals(21L, bo.getId());
    }

    @Test void updateRejectsUnavailableBindingAndRechecksSavedBindingWhenOmitted() {
        var mapper = mock(ChatSessionMapper.class); var guard = mock(KnowledgeBaseAccessService.class);
        var service = new ChatSessionServiceImpl(mapper, mock(ChatPromptService.class), guard);
        var stored = new ChatSession(); stored.setId(21L); stored.setKnowledgeId("42");
        when(mapper.selectById(21L)).thenReturn(stored);
        when(guard.requireAccessible(anyString())).thenThrow(new ServiceException("unavailable"));
        var bo = new ChatSessionBo(); bo.setId(21L); bo.setKnowledgeId("43");
        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        bo.setKnowledgeId(null);
        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        verify(guard).requireAccessible("43"); verify(guard).requireAccessible("42");
        verify(mapper, never()).updateById(any(ChatSession.class));
    }

    @Test void sessionUpdateCanReplaceOrClearBindingAndOmissionPreservesIt() {
        var mapper = mock(ChatSessionMapper.class); var guard = mock(KnowledgeBaseAccessService.class);
        var service = new ChatSessionServiceImpl(mapper, mock(ChatPromptService.class), guard);
        var stored = new ChatSession(); stored.setId(21L); stored.setKnowledgeId("42");
        when(mapper.selectById(21L)).thenReturn(stored);
        when(mapper.updateById(any(ChatSession.class))).thenReturn(1);
        var bo = new ChatSessionBo(); bo.setId(21L);
        try (var conversion = mockStatic(MapstructUtils.class)) {
            conversion.when(() -> MapstructUtils.convert(bo, ChatSession.class))
                .thenAnswer(call -> new org.ruoyi.domain.bo.chat.ChatSessionBoToChatSessionMapperImpl().convert(bo));
            assertTrue(service.updateByBo(bo));
            bo.setKnowledgeId("43"); assertTrue(service.updateByBo(bo));
            bo.setKnowledgeId(""); assertTrue(service.updateByBo(bo));
        }
        var entities = org.mockito.ArgumentCaptor.forClass(ChatSession.class);
        verify(mapper, times(3)).updateById(entities.capture());
        assertEquals("42", entities.getAllValues().get(0).getKnowledgeId());
        assertEquals("43", entities.getAllValues().get(1).getKnowledgeId());
        assertNull(entities.getAllValues().get(2).getKnowledgeId());
        verify(guard).requireAccessible("42"); verify(guard).requireAccessible("43");
    }

    private static KnowledgeBaseAccessService access() {
        return new KnowledgeBaseAccessService(mock(org.ruoyi.mapper.knowledge.KnowledgeInfoMapper.class),
            mock(org.ruoyi.mapper.knowledge.KnowledgeAttachMapper.class));
    }
}
