package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.controller.knowledge.KnowledgeBaseController;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class KnowledgeBaseAccessServiceTest {
    private KnowledgeInfoMapper mapper;
    private KnowledgeAttachMapper documents;
    private KnowledgeBaseAccessService service;
    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach void setup() {
        mapper = mock(KnowledgeInfoMapper.class); documents = mock(KnowledgeAttachMapper.class);
        service = new KnowledgeBaseAccessService(mapper, documents);
        login = mockStatic(LoginHelper.class); tenant = mockStatic(TenantHelper.class);
        login.when(LoginHelper::isLogin).thenReturn(true);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getTenantId).thenReturn("000007");
        tenant.when(TenantHelper::getTenantId).thenReturn("000007");
    }
    @AfterEach void close() { tenant.close(); login.close(); }

    @Test void listingUsesExplicitTenantAndGroupedPublicOrOwnerWithBoundKeywordAndBoundedPage() throws Exception {
        when(mapper.selectVoPage(any(IPage.class), any(Wrapper.class))).thenAnswer(call -> {
            IPage<KnowledgeInfo> page = call.getArgument(0);
            assertEquals(1, page.getCurrent()); assertEquals(100, page.getSize());
            QueryWrapper<KnowledgeInfo> query = call.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("tenant_id ="), sql);
            assertTrue(sql.contains("status ="), sql);
            assertTrue(query.getParamNameValuePairs().containsValue("0"));
            assertTrue(sql.matches("(?s).*AND \\(share = .* OR user_id = .*\\).*"), sql);
            assertTrue(sql.contains("name LIKE"), sql);
            assertTrue(query.getParamNameValuePairs().containsValue("000007"));
            assertTrue(query.getParamNameValuePairs().containsValue(7L));
            assertTrue(query.getParamNameValuePairs().containsValue(1));
            assertTrue(query.getParamNameValuePairs().containsValue("%x' OR 1=1%"));
            assertEquals("id,name,description,update_time", query.getSqlSelect());
            var result = new Page<KnowledgeInfoVo>(page.getCurrent(), page.getSize(), 25);
            result.setRecords(List.of(knowledge())); return result;
        });
        when(documents.countAccessibleDocuments("000007", List.of(42L))).thenReturn(List.of(Map.of("knowledgeId", 42L, "documentCount", 3L)));
        var response = new KnowledgeBaseController(service).list("x' OR 1=1", new PageQuery(1000, -5));
        var json = new ObjectMapper().valueToTree(response);
        assertEquals(25, json.path("total").asInt());
        var summary = json.path("rows").get(0);
        assertEquals(Set.of("id", "name", "description", "documentCount", "updateTime"), keys(summary));
        assertTrue(summary.path("id").isTextual()); assertEquals("42", summary.path("id").asText());
        assertEquals(3, summary.path("documentCount").asInt());
        assertFalse(summary.toString().contains("private-embedding"));
    }

    @Test void detailUsesTheSameSafeSummaryInRDataAndChecksAccess() {
        when(mapper.selectVoOne(any(Wrapper.class))).thenReturn(knowledge());
        var response = new KnowledgeBaseController(service).getInfo("42");
        var summary = new ObjectMapper().valueToTree(response).path("data");
        assertEquals(Set.of("id", "name", "description", "documentCount", "updateTime"), keys(summary));
        assertEquals("Java guide", summary.path("name").asText());
        verify(mapper).selectVoOne(argThat((Wrapper<KnowledgeInfo> wrapper) -> {
            var query = (QueryWrapper<KnowledgeInfo>) wrapper;
            String sql = query.getSqlSegment();
            return sql.contains("tenant_id =") && sql.contains("share =") && sql.contains("OR user_id =")
                && sql.contains("id =") && query.getParamNameValuePairs().containsValue(42L);
        }));
    }

    @Test void anonymousCannotListOrReadEvenPublicKnowledge() {
        login.when(LoginHelper::isLogin).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.list(null, new PageQuery(null, null)));
        assertThrows(ServiceException.class, () -> service.requireAccessible("42"));
        verifyNoInteractions(mapper, documents);
    }

    @Test void disabledTenantPluginStillUsesAuthenticatedTenantAndMissingIdentityFailsClosed() {
        tenant.when(TenantHelper::getTenantId).thenReturn(null);
        when(mapper.selectVoOne(any(Wrapper.class))).thenReturn(knowledge());
        service.requireAccessible("42");
        verify(mapper).selectVoOne(argThat((Wrapper<KnowledgeInfo> wrapper) -> {
            var query = (QueryWrapper<KnowledgeInfo>) wrapper; query.getSqlSegment();
            return query.getParamNameValuePairs().containsValue("000007");
        }));
        login.when(LoginHelper::getTenantId).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.requireAccessible("42"));
    }

    @Test void malformedOrUnavailableKnowledgeNeverProducesAnAccessibleBinding() {
        for (String id : Arrays.asList(null, "", " ", "0", "-1", "1 OR 1=1", "9223372036854775808")) {
            assertThrows(ServiceException.class, () -> service.requireAccessible(id), String.valueOf(id));
        }
        verifyNoInteractions(mapper);
        // Both another owner's private record and a record in another tenant are filtered out by the guard query.
        assertThrows(ServiceException.class, () -> service.requireAccessible("42"));
        assertThrows(ServiceException.class, () -> service.requireAccessible("43"));
    }

    @Test void defaultPaginationIsFiniteAndEmptyListSkipsDocumentQuery() {
        when(mapper.selectVoPage(any(IPage.class), any(Wrapper.class))).thenAnswer(call -> {
            IPage<?> page = call.getArgument(0); assertEquals(12, page.getSize());
            return new Page<KnowledgeInfoVo>(1, 12, 0);
        });
        assertTrue(service.list(null, new PageQuery(null, null)).getRows().isEmpty());
        verifyNoInteractions(documents);
    }

    @Test void documentCountSqlScopesExplicitlyToCurrentTenant() throws Exception {
        String sql = String.join(" ", KnowledgeAttachMapper.class.getMethod("countAccessibleDocuments", String.class, List.class)
            .getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertTrue(sql.contains("tenant_id = #{tenantId}"), sql);
        assertTrue(sql.contains("knowledge_id IN"), sql);
    }

    @Test void trustedHandshakePrincipalScopesQueriesWithoutReadingHttpLoginOrTenantState() {
        var principal = new org.ruoyi.common.core.domain.model.LoginUser();
        principal.setUserId(7L); principal.setTenantId("000007");
        when(mapper.selectVoOne(any(Wrapper.class))).thenReturn(knowledge());
        assertEquals("42", service.resolveForChat("42", null, principal));
        assertEquals(42L, service.requireAccessible("42", principal).getId());
        verify(mapper, times(2)).selectVoOne(argThat((Wrapper<KnowledgeInfo> wrapper) -> {
            var query = (QueryWrapper<KnowledgeInfo>) wrapper; query.getSqlSegment();
            return query.getParamNameValuePairs().containsValue("000007")
                && query.getParamNameValuePairs().containsValue(7L);
        }));
        login.verifyNoInteractions(); tenant.verifyNoInteractions();
    }

    @Test void incompleteHandshakePrincipalsCannotResolveEvenAnUnboundSelection() {
        var principal = new org.ruoyi.common.core.domain.model.LoginUser(); principal.setUserId(7L);
        assertThrows(ServiceException.class, () -> service.resolveForChat(null, null, null));
        assertThrows(ServiceException.class, () -> service.resolveForChat(null, null, principal));
        principal.setTenantId("000007"); principal.setUserId(0L);
        assertThrows(ServiceException.class, () -> service.requireAccessible("42", principal));
        verifyNoInteractions(mapper, documents);
    }

    @Test void disabledKnowledgeRejectsSummaryAndExistingHttpAndWebSocketBindings() throws Exception {
        var disabled = new ObjectMapper().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue("{\"id\":42,\"status\":\"1\",\"name\":\"disabled\"}", KnowledgeInfoVo.class);
        when(mapper.selectVoOne(any(Wrapper.class))).thenReturn(disabled);
        assertEquals("知识库已停用", assertThrows(KnowledgeBaseDisabledException.class, () -> service.getSummary("42")).getMessage());
        assertThrows(KnowledgeBaseDisabledException.class, () -> service.resolveForChat("42", null));
        var principal = new org.ruoyi.common.core.domain.model.LoginUser();
        principal.setUserId(7L); principal.setTenantId("000007");
        assertThrows(KnowledgeBaseDisabledException.class, () -> service.resolveForChat("42", null, principal));
        verifyNoInteractions(documents);
    }

    private static KnowledgeInfoVo knowledge() {
        var vo = new KnowledgeInfoVo(); vo.setId(42L); vo.setStatus("0"); vo.setName("Java guide"); vo.setDescription("Reference");
        vo.setEmbeddingModel("private-embedding"); return vo;
    }
    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode node) {
        Set<String> result = new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }
}
