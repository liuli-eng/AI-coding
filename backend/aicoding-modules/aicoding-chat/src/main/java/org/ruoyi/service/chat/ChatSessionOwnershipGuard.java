package org.ruoyi.service.chat;

import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.springframework.stereotype.Component;

/** Fail-closed ownership check shared by HTTP and WebSocket chat entry points. */
@Component
@RequiredArgsConstructor
public class ChatSessionOwnershipGuard {

    private final IChatSessionService chatSessionService;
    private final org.ruoyi.service.learning.CourseMaterialService courseMaterials;

    public ChatSessionVo requireOwned(Long userId, Long sessionId) {
        return requireOwned(userId,sessionId,null);
    }

    public ChatSessionVo requireOwned(Long userId, Long sessionId, String tenantId) {
        if (userId == null || userId <= 0 || sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("An authenticated chat session is required");
        }
        ChatSessionVo session = chatSessionService.queryById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            // Do not reveal whether a guessed identifier exists.
            throw new IllegalArgumentException("Chat session is unavailable");
        }
        if ("learning".equals(session.getChatMode())) {
            String tenant=tenantId;
            if (tenant==null) {
                tenant=org.ruoyi.common.tenant.helper.TenantHelper.getTenantId();
                if (org.ruoyi.common.core.utils.StringUtils.isBlank(tenant))
                    tenant=org.ruoyi.common.satoken.utils.LoginHelper.getTenantId();
            }
            var current=courseMaterials.resolveSessionContext(sessionId,userId,tenant);
            session.setCourseMaterialIds(current.materialIds());
            session.setLearningContext(current.learningContext());
        }
        return session;
    }
}
