package org.ruoyi.domain.vo.knowledge;
import java.util.Date;
import java.util.Set;
public record KnowledgeSettingsVo(String systemName, Set<String> allowedTypes, boolean overwriteSameName,
    String failureMessage, Double similarityThreshold, int version, Date updateTime, String updatedBy) {}
