package org.ruoyi.domain.entity.knowledge;
import lombok.Data;
import java.util.Date;
@Data
public class KnowledgeSettings {
    private String tenantId;
    private String systemName;
    private String allowedTypes;
    private Boolean overwriteSameName;
    private String failureMessage;
    private Double similarityThreshold;
    private Integer version;
    private Long updateBy;
    private Date updateTime;
    private String updatedBy;
}
