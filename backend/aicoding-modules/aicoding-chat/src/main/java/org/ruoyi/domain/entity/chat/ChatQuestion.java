package org.ruoyi.domain.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;
import java.util.Date;

/** One immutable question/answer snapshot, independent of optional trace collection. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_question")
public class ChatQuestion extends TenantEntity {
    @TableId @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    private String modelName;
    private String chatMode;
    private String question;
    private String answer;
    private String answerStatus;
    private String retrievalStatus;
    private String knowledgeNames;
    private String citations;
    private String traceId;
    private Long totalTokens;
    private Long durationMs;
    private Date startedAt;
    private Date finishedAt;
    private String errorMessage;
    private String issueType;
    private String reviewStatus;
    private String reviewNote;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewedBy;
    private Date reviewedAt;
    private Integer version;
}
