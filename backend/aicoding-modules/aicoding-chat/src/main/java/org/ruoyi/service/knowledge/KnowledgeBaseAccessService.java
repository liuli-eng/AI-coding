package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.domain.vo.knowledge.KnowledgeBaseSummaryVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared fail-closed policy for the ordinary-user picker, session binding and retrieval. */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseAccessService {
    private static final String UNAVAILABLE = "知识库不可用";
    private final KnowledgeInfoMapper knowledgeMapper;
    private final KnowledgeAttachMapper documentMapper;

    public TableDataInfo<KnowledgeBaseSummaryVo> list(String keyword, PageQuery pageQuery) {
        Identity identity = currentIdentity();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 12
            : Math.max(1, Math.min(100, pageQuery.getPageSize()));
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1
            : Math.max(1, Math.min(1_000_000, pageQuery.getPageNum()));
        String search = keyword == null ? null : keyword.trim();
        if (search != null && search.length() > 100) throw new ServiceException("搜索关键词不能超过100个字符");
        var query = accessibleQuery(identity).select("id", "name", "description", "update_time")
            .eq("status", "0")
            .like(StringUtils.isNotBlank(search), "name", search)
            .orderByDesc("update_time", "id");
        Page<KnowledgeInfoVo> result = knowledgeMapper.selectVoPage(new Page<KnowledgeInfo>(pageNum, pageSize), query);
        Map<Long, Integer> counts = documentCounts(identity.tenantId(), result.getRecords());
        var rows = result.getRecords().stream().map(info -> summary(info, counts.getOrDefault(info.getId(), 0))).toList();
        return new TableDataInfo<>(rows, result.getTotal());
    }

    public KnowledgeBaseSummaryVo getSummary(String id) {
        Identity identity = currentIdentity();
        KnowledgeInfoVo info = requireAccessible(identity, id);
        return summary(info, documentCounts(identity.tenantId(), List.of(info)).getOrDefault(info.getId(), 0));
    }

    /** Internal result may contain configuration; only getSummary/list may be returned over HTTP. */
    public KnowledgeInfoVo requireAccessible(String id) {
        return requireAccessible(currentIdentity(), id);
    }

    /** Caller must supply the principal validated by the server's authenticated handshake. */
    public KnowledgeInfoVo requireAccessible(String id, LoginUser authenticatedPrincipal) {
        return requireAccessible(trustedIdentity(authenticatedPrincipal), id);
    }

    public String resolveForChat(String savedId, String requestedId) {
        String selected = selectForChat(savedId, requestedId);
        if (selected != null) requireAccessible(selected);
        return selected;
    }

    /** WebSocket callbacks have no HTTP LoginHelper context; never derive identity from payloads. */
    public String resolveForChat(String savedId, String requestedId, LoginUser authenticatedPrincipal) {
        Identity identity = trustedIdentity(authenticatedPrincipal);
        String selected = selectForChat(savedId, requestedId);
        if (selected != null) requireAccessible(identity, selected);
        return selected;
    }

    private String selectForChat(String savedId, String requestedId) {
        String saved = StringUtils.isBlank(savedId) ? null : savedId;
        String requested = StringUtils.isBlank(requestedId) ? null : requestedId;
        if (saved != null && requested != null && !saved.equals(requested)) {
            throw new ServiceException("请求知识库与会话绑定不一致，请新建会话");
        }
        return saved == null ? requested : saved;
    }

    private Identity trustedIdentity(LoginUser principal) {
        if (principal == null || principal.getUserId() == null || principal.getUserId() <= 0
            || StringUtils.isBlank(principal.getTenantId())) throw new ServiceException("请先登录");
        return new Identity(principal.getUserId(), principal.getTenantId());
    }

    private KnowledgeInfoVo requireAccessible(Identity identity, String id) {
        Long parsedId;
        try {
            if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            parsedId = Long.valueOf(id);
        } catch (NumberFormatException error) {
            throw new ServiceException(UNAVAILABLE);
        }
        KnowledgeInfoVo info = knowledgeMapper.selectVoOne(accessibleQuery(identity).eq("id", parsedId));
        if (info == null) throw new ServiceException(UNAVAILABLE);
        KnowledgeStatusService.requireEnabledStatus(info.getStatus());
        return info;
    }

    private QueryWrapper<KnowledgeInfo> accessibleQuery(Identity identity) {
        // KnowledgeInfo extends BaseEntity, so tenant enforcement cannot depend on entity inheritance
        // or on the optional tenant interceptor being enabled for this request.
        return new QueryWrapper<KnowledgeInfo>().eq("tenant_id", identity.tenantId())
            .and(query -> query.eq("share", 1).or().eq("user_id", identity.userId()));
    }

    private Identity currentIdentity() {
        if (!LoginHelper.isLogin()) throw new ServiceException("请先登录");
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        // TenantHelper returns null when the tenant plugin is disabled. The authenticated tenant
        // still scopes these APIs; never fall back to an unrestricted/default tenant.
        if (StringUtils.isBlank(tenantId)) tenantId = LoginHelper.getTenantId();
        if (userId == null || userId <= 0 || StringUtils.isBlank(tenantId)) throw new ServiceException("请先登录");
        return new Identity(userId, tenantId);
    }

    private Map<Long, Integer> documentCounts(String tenantId, List<KnowledgeInfoVo> records) {
        Map<Long, Integer> counts = new HashMap<>();
        if (records.isEmpty()) return counts;
        var ids = records.stream().map(KnowledgeInfoVo::getId).toList();
        for (var row : documentMapper.countAccessibleDocuments(tenantId, ids)) {
            Object id = row.getOrDefault("knowledgeId", row.get("knowledgeid"));
            Object count = row.getOrDefault("documentCount", row.get("documentcount"));
            if (id instanceof Number key && count instanceof Number value) counts.put(key.longValue(), value.intValue());
        }
        return counts;
    }

    private KnowledgeBaseSummaryVo summary(KnowledgeInfoVo info, int documentCount) {
        return new KnowledgeBaseSummaryVo(String.valueOf(info.getId()), info.getName(), info.getDescription(), documentCount, info.getUpdateTime());
    }

    private record Identity(Long userId, String tenantId) {}
}
