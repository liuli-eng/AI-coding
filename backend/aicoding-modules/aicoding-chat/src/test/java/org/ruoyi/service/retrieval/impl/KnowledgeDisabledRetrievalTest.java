package org.ruoyi.service.retrieval.impl;

import org.junit.jupiter.api.*;
import org.ruoyi.common.trace.config.TraceProperties;
import org.ruoyi.common.trace.service.TraceRecordService;
import org.ruoyi.domain.bo.vector.QueryVectorBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.factory.RerankModelFactory;
import org.ruoyi.mapper.knowledge.*;
import org.ruoyi.service.knowledge.*;
import org.ruoyi.service.vector.VectorStoreService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class KnowledgeDisabledRetrievalTest {
    private final KnowledgeInfoMapper mapper=mock(KnowledgeInfoMapper.class);
    private final VectorStoreService vectors=mock(VectorStoreService.class);
    private final KnowledgeAttachMapper documents=mock(KnowledgeAttachMapper.class);
    private final KnowledgeInfo info=new KnowledgeInfo();
    private final KnowledgeRetrievalServiceImpl service=new KnowledgeRetrievalServiceImpl(vectors,mock(RerankModelFactory.class),mock(KnowledgeFragmentMapper.class),mock(TraceRecordService.class),new TraceProperties(),new KnowledgeStatusService(mapper),new org.ruoyi.service.knowledge.KnowledgeDocumentAvailability(documents));
    private QueryVectorBo query(){var q=new QueryVectorBo();q.setKid("42");q.setQuery("question");q.setEnableHybrid(false);return q;}
    @BeforeEach void setup(){var row=new org.ruoyi.domain.entity.knowledge.KnowledgeAttach();row.setDocId("doc");row.setBusinessState("0");when(documents.documentStates(42L)).thenReturn(List.of(row));info.setStatus("0");when(mapper.selectById(42L)).thenReturn(info);when(vectors.search(any())).thenReturn(List.of(KnowledgeRetrievalVo.builder().id("one").docId("doc").content("private text").score(.9).build()));}

    @Test void cachedTextIsBlockedAfterDisableAndAvailableAgainAfterEnable(){
        assertEquals(1,service.retrieve(query()).size());info.setStatus("1");
        assertThrows(KnowledgeBaseDisabledException.class,()->service.retrieve(query()));
        verify(vectors,times(1)).search(any());
        info.setStatus("0");assertEquals(1,service.retrieve(query()).size());
        verify(vectors,times(1)).search(any());
    }
    @Test void cachedDocumentIsRecheckedAfterDisableReplacementAndDeletion(){
        assertEquals(1,service.retrieve(query()).size());
        var row=new org.ruoyi.domain.entity.knowledge.KnowledgeAttach();row.setDocId("doc");row.setBusinessState("1");
        when(documents.documentStates(42L)).thenReturn(List.of(row));assertTrue(service.retrieve(query()).isEmpty());
        row.setBusinessState("0");assertEquals(1,service.retrieve(query()).size());
        row.setDocId("replacement");assertTrue(service.retrieve(query()).isEmpty());
        when(documents.documentStates(42L)).thenReturn(List.of());assertTrue(service.retrieve(query()).isEmpty());verify(vectors,times(1)).search(any());
    }
    @Test void disableDuringVectorLookupCannotReturnOrCacheText(){
        when(vectors.search(any())).thenAnswer(call->{info.setStatus("1");return List.of(KnowledgeRetrievalVo.builder().id("one").docId("doc").content("private text").score(.9).build());});
        assertThrows(KnowledgeBaseDisabledException.class,()->service.retrieve(query()));
        info.setStatus("0");doReturn(List.of()).when(vectors).search(any());
        assertTrue(service.retrieve(query()).isEmpty());verify(vectors,times(2)).search(any());
    }
}
