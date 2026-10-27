package org.ruoyi.service.chat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.chat.ChatQuestionQuery;
import org.ruoyi.domain.entity.chat.ChatQuestion;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.mapper.chat.ChatQuestionMapper;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatQuestionService {
    private final ChatQuestionMapper mapper;
    private final KnowledgeInfoMapper knowledgeMapper;
    private final KnowledgeAttachMapper attachMapper;

    public Long start(ChatQuestion question) {
        question.setAnswerStatus("running");
        question.setRetrievalStatus("not_used");
        question.setReviewStatus("none");
        question.setIssueType("none");
        question.setVersion(0);
        question.setStartedAt(new Date());
        mapper.insert(question);
        return question.getId();
    }

    /** Explicit tenant predicate also protects asynchronous model callbacks without a login context. */
    public void finish(ChatQuestion question) {
        if (question.getId() == null || StringUtils.isBlank(question.getTenantId())) {
            throw new IllegalArgumentException("Question id and tenant are required");
        }
        // Callback threads have no login session; explicit predicates preserve isolation
        // without altering the request's dynamic tenant or its Redis state.
        org.ruoyi.common.tenant.helper.TenantHelper.ignore(() -> { mapper.update(question,
            Wrappers.<ChatQuestion>lambdaUpdate().eq(ChatQuestion::getId, question.getId())
                .eq(ChatQuestion::getTenantId, question.getTenantId())
                .eq(ChatQuestion::getAnswerStatus, "running")); });
    }

    public TableDataInfo<ChatQuestion> page(ChatQuestionQuery query, PageQuery pageQuery) {
        var wrapper = Wrappers.<ChatQuestion>lambdaQuery()
            .select(ChatQuestion.class, field -> !Set.of("answer", "citations", "reviewNote", "errorMessage").contains(field.getProperty()))
            .like(StringUtils.isNotBlank(query.getUserName()), ChatQuestion::getUserName, query.getUserName())
            .eq(StringUtils.isNotBlank(query.getModelName()), ChatQuestion::getModelName, query.getModelName())
            .eq(StringUtils.isNotBlank(query.getReviewStatus()), ChatQuestion::getReviewStatus, query.getReviewStatus())
            .ge(query.getBeginTime() != null, ChatQuestion::getStartedAt, query.getBeginTime())
            .le(query.getEndTime() != null, ChatQuestion::getStartedAt, query.getEndTime());
        if (StringUtils.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(ChatQuestion::getQuestion, query.getKeyword()).or().like(ChatQuestion::getAnswer, query.getKeyword()));
        }
        if ("no_match".equals(query.getAnswerStatus())) wrapper.eq(ChatQuestion::getRetrievalStatus, "no_match");
        else if (StringUtils.isNotBlank(query.getAnswerStatus())) wrapper.eq(ChatQuestion::getAnswerStatus, query.getAnswerStatus());
        if (Boolean.TRUE.equals(query.getExceptionsOnly())) {
            wrapper.and(w -> w.eq(ChatQuestion::getAnswerStatus, "failed")
                .or().in(ChatQuestion::getRetrievalStatus, "no_match", "failed")
                .or().ne(ChatQuestion::getIssueType, "none").or().eq(ChatQuestion::getReviewStatus, "pending"));
        }
        // Do not accept raw client-supplied SQL ordering.
        Page<ChatQuestion> page = new Page<>(pageQuery.getPageNum() == null ? 1 : Math.max(1, pageQuery.getPageNum()), pageQuery.getPageSize() == null ? 20 : Math.min(100, Math.max(1, pageQuery.getPageSize())));
        return TableDataInfo.build(mapper.selectPage(page, wrapper.orderByDesc(ChatQuestion::getStartedAt, ChatQuestion::getId)));
    }

    public ChatQuestion detail(Long id) {
        ChatQuestion result = mapper.selectById(id);
        if (result == null) throw new ServiceException("问答记录不存在或无权访问");
        return result;
    }

    public void review(Long id, String status, String note, Integer version) {
        if (version == null || version < 0 || status == null) throw new ServiceException("无效的复核请求");
        if (!Set.of("pending", "resolved", "ignored").contains(status)) throw new ServiceException("无效的复核状态");
        if ("resolved".equals(status) && (note == null || note.isBlank())) throw new ServiceException("请填写处理结果");
        ChatQuestion current = detail(id);
        if ("running".equals(current.getAnswerStatus())) throw new ServiceException("回答进行中，请稍后复核");
        int changed = mapper.update(null, Wrappers.<ChatQuestion>lambdaUpdate()
            .eq(ChatQuestion::getId, id).eq(ChatQuestion::getVersion, version)
            .set(ChatQuestion::getReviewStatus, status).set(ChatQuestion::getReviewNote, note)
            .set(ChatQuestion::getReviewedBy, LoginHelper.getUserId()).set(ChatQuestion::getReviewedAt, new Date())
            .set(ChatQuestion::getVersion, version + 1));
        if (changed == 0) throw new ServiceException("记录已被其他人更新，请刷新后重试");
    }

    /** Authenticated users can only flag their own completed answers, once. */
    public void feedback(Long id, Long userId) {
        ChatQuestion current = detail(id);
        if (userId == null || !userId.equals(current.getUserId())) throw new ServiceException("问答记录不存在或无权访问");
        if ("running".equals(current.getAnswerStatus())) throw new ServiceException("请等待回答完成后再反馈");
        if ("dissatisfied".equals(current.getIssueType())) return;
        int changed = mapper.update(null, Wrappers.<ChatQuestion>lambdaUpdate()
            .eq(ChatQuestion::getId, id).eq(ChatQuestion::getUserId, userId)
            .eq(ChatQuestion::getVersion, current.getVersion())
            .set(ChatQuestion::getIssueType, "dissatisfied").set(ChatQuestion::getReviewStatus, "pending")
            .set(ChatQuestion::getVersion, current.getVersion() + 1));
        if (changed == 0) throw new ServiceException("记录已更新，请重试");
    }

    public Map<String, Object> overview() {
        // The reporting timezone is explicit, independent of the server's timezone.
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        Date begin = Date.from(today.atStartOfDay(zone).toInstant());
        Date end = Date.from(today.plusDays(1).atStartOfDay(zone).toInstant());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", mapper.totals(begin, end));
        result.put("models", mapper.models(begin, end));
        result.put("knowledgeCount", knowledgeMapper.selectCount(Wrappers.<KnowledgeInfo>lambdaQuery()));
        result.put("documentCount", attachMapper.selectCount(Wrappers.<KnowledgeAttach>lambdaQuery()));
        result.put("failedDocuments", attachMapper.selectCount(Wrappers.<KnowledgeAttach>lambdaQuery().eq(KnowledgeAttach::getStatus, 3)));
        result.put("pendingReviews", mapper.selectCount(Wrappers.<ChatQuestion>lambdaQuery().eq(ChatQuestion::getReviewStatus, "pending")));
        result.put("firstRecordedAt", mapper.selectObjs(Wrappers.<ChatQuestion>query().select("MIN(started_at)")).stream().filter(Objects::nonNull).findFirst().orElse(null));
        result.put("reportDate", today.toString());
        result.put("timezone", zone.toString());
        result.put("updatedAt", new Date());
        return result;
    }
}
