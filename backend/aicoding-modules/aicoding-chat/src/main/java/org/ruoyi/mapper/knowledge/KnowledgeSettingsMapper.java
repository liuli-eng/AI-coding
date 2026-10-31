package org.ruoyi.mapper.knowledge;
import org.apache.ibatis.annotations.*;
import org.ruoyi.domain.entity.knowledge.KnowledgeSettings;
@Mapper
public interface KnowledgeSettingsMapper {
    @Select("SELECT s.*,COALESCE(u.nick_name,u.user_name,CAST(s.update_by AS CHAR)) AS updated_by FROM knowledge_settings s LEFT JOIN sys_user u ON u.user_id=s.update_by AND u.tenant_id=s.tenant_id WHERE s.tenant_id=#{tenantId}")
    KnowledgeSettings get(String tenantId);
    @Insert("INSERT INTO knowledge_settings(tenant_id,system_name,allowed_types,overwrite_same_name,failure_message,similarity_threshold,version,update_by,update_time) VALUES(#{tenantId},#{systemName},#{allowedTypes},#{overwriteSameName},#{failureMessage},#{similarityThreshold},1,#{updateBy},#{updateTime})")
    int insert(KnowledgeSettings settings);
    @Update("UPDATE knowledge_settings SET system_name=#{systemName},allowed_types=#{allowedTypes},overwrite_same_name=#{overwriteSameName},failure_message=#{failureMessage},similarity_threshold=#{similarityThreshold},version=version+1,update_by=#{updateBy},update_time=#{updateTime} WHERE tenant_id=#{tenantId} AND version=#{version}")
    int update(KnowledgeSettings settings);
}
