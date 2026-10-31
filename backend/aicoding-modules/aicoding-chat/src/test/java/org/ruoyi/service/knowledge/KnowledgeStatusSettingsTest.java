package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.core.utils.MapstructUtils;
import org.ruoyi.domain.bo.knowledge.KnowledgeInfoBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.ruoyi.service.knowledge.impl.KnowledgeInfoServiceImpl;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("dev")
class KnowledgeStatusSettingsTest {
    @BeforeAll static void initializeConverter() {
        var context = new org.springframework.context.support.GenericApplicationContext();
        context.registerBean(io.github.linpeilie.Converter.class, () -> mock(io.github.linpeilie.Converter.class));
        context.refresh();
        new cn.hutool.extra.spring.SpringUtil().setApplicationContext(context);
    }

    private final KnowledgeInfoMapper mapper = mock(KnowledgeInfoMapper.class);
    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final KnowledgeSettingsService settings = mock(KnowledgeSettingsService.class);
    private final KnowledgeInfoServiceImpl service = new KnowledgeInfoServiceImpl(mapper, null, null, null, retrieval, null, settings);

    @Test void newKnowledgeStartsEnabledEvenWhenCallerSuppliesDisabled() {
        var form = new KnowledgeInfoBo();
        form.setStatus("1");
        when(settings.current()).thenReturn(KnowledgeSettingsService.defaults());
        var mapped = new KnowledgeInfo();
        mapped.setStatus("1");
        try (var conversion = mockStatic(MapstructUtils.class)) {
            conversion.when(() -> MapstructUtils.convert(form, KnowledgeInfo.class)).thenReturn(mapped);
            when(mapper.insert(any(KnowledgeInfo.class))).thenAnswer(call -> {
                KnowledgeInfo inserted = call.getArgument(0);
                assertEquals("0", inserted.getStatus());
                assertEquals(0.5, inserted.getSimilarityThreshold());
                inserted.setId(42L);
                return 1;
            });
            assertTrue(service.insertByBo(form));
            assertEquals(42L, form.getId());
        }
    }

    @Test void staleSettingsFormCannotReenableKnowledgeDisabledInAnotherWindow() {
        var form = new KnowledgeInfoBo();
        form.setId(42L);
        form.setStatus("0");
        var mapped = new KnowledgeInfo();
        mapped.setId(42L);
        mapped.setStatus("0");
        try (var conversion = mockStatic(MapstructUtils.class)) {
            conversion.when(() -> MapstructUtils.convert(form, KnowledgeInfo.class)).thenReturn(mapped);
            when(mapper.updateById(any(KnowledgeInfo.class))).thenAnswer(call -> {
                KnowledgeInfo updated = call.getArgument(0);
                assertNull(updated.getStatus(), "Settings updates must omit status and preserve the saved disabled state");
                return 1;
            });
            assertTrue(service.updateByBo(form));
            verify(retrieval).invalidateKnowledge("42");
        }
    }
}
