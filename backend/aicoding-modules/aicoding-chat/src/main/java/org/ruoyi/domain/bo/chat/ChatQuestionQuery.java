package org.ruoyi.domain.bo.chat;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;

@Data
public class ChatQuestionQuery {
    private String userName;
    private String keyword;
    private String modelName;
    private String answerStatus;
    private String reviewStatus;
    private Boolean exceptionsOnly;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
}
