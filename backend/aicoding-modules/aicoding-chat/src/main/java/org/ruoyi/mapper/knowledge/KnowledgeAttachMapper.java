package org.ruoyi.mapper.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.KnowledgeAttachVo;
import org.ruoyi.common.mybatis.core.mapper.BaseMapperPlus;
import java.util.List;
import java.util.Map;

/**
 * 知识库附件Mapper接口
 *
 * @author ageerle
 * @date 2025-12-17
 */
@Mapper
public interface KnowledgeAttachMapper extends BaseMapperPlus<KnowledgeAttach, KnowledgeAttachVo> {

    /**
     * 统计指定知识库下的文档数量
     */
    @Select("SELECT COUNT(*) FROM knowledge_attach WHERE knowledge_id = #{knowledgeId}")
    int countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("<script>SELECT knowledge_id AS knowledgeId, COUNT(*) AS documentCount " +
            "FROM knowledge_attach WHERE knowledge_id IN " +
            "<foreach collection='knowledgeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY knowledge_id</script>")
    List<Map<String, Object>> countByKnowledgeIds(@Param("knowledgeIds") List<Long> knowledgeIds);
    /** Ordinary-user counts remain tenant-scoped even if tenant interception is disabled. */
    @Select("<script>SELECT knowledge_id AS knowledgeId, COUNT(*) AS documentCount " +
            "FROM knowledge_attach WHERE tenant_id = #{tenantId} AND knowledge_id IN " +
            "<foreach collection='knowledgeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY knowledge_id</script>")
    List<Map<String, Object>> countAccessibleDocuments(@Param("tenantId") String tenantId,
                                                       @Param("knowledgeIds") List<Long> knowledgeIds);

    @Select("<script>SELECT id,name,status FROM knowledge_info WHERE tenant_id=#{tenantId} "
        + "<if test='keyword != null and keyword != &quot;&quot;'>AND name LIKE CONCAT('%',#{keyword},'%')</if> ORDER BY id DESC</script>")
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.ruoyi.domain.vo.knowledge.KnowledgeOptionVo> knowledgeOptions(
        com.baomidou.mybatisplus.core.metadata.IPage<org.ruoyi.domain.vo.knowledge.KnowledgeOptionVo> page,@Param("tenantId") String tenantId,@Param("keyword") String keyword);
    String DOCUMENT_WHERE=" WHERE a.tenant_id=#{tenantId} AND k.tenant_id=#{tenantId} "
        + "<if test='q.knowledgeId != null'> AND a.knowledge_id=#{q.knowledgeId}</if>"
        + "<if test='q.name != null and q.name != &quot;&quot;'> AND a.name LIKE CONCAT('%',#{q.name},'%')</if>"
        + "<if test='q.type != null and q.type != &quot;&quot;'> AND a.type=#{q.type}</if>"
        + "<if test='q.businessState != null and q.businessState != &quot;&quot;'> AND a.business_state=#{q.businessState}</if>";
    @Select("<script>SELECT a.*,k.name AS knowledge_name,COALESCE(u.nick_name,u.user_name,CAST(a.create_by AS CHAR)) AS uploaded_by "
        + "FROM knowledge_attach a JOIN knowledge_info k ON k.id=a.knowledge_id "
        + "LEFT JOIN sys_user u ON u.user_id=a.create_by AND u.tenant_id=#{tenantId}" + DOCUMENT_WHERE
        + "<if test='q.status != null'> AND a.status=#{q.status}</if> ORDER BY a.create_time DESC,a.id DESC</script>")
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<KnowledgeAttachVo> workbench(
        com.baomidou.mybatisplus.core.metadata.IPage<KnowledgeAttachVo> page,@Param("tenantId") String tenantId,@Param("q") org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo query);
    @Select("<script>SELECT COUNT(*) AS total,COALESCE(SUM(a.status=0),0) AS waiting,COALESCE(SUM(a.status=1),0) AS processing,COALESCE(SUM(a.status=2),0) AS completed,COALESCE(SUM(a.status=3),0) AS failed FROM knowledge_attach a JOIN knowledge_info k ON k.id=a.knowledge_id" + DOCUMENT_WHERE + "</script>")
    org.ruoyi.domain.vo.knowledge.KnowledgeDocumentStats statistics(@Param("tenantId") String tenantId,@Param("q") org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo query);
    @Select("SELECT doc_id,business_state FROM knowledge_attach WHERE knowledge_id=#{knowledgeId}")
    List<KnowledgeAttach> documentStates(Long knowledgeId);
    @Select("SELECT doc_id FROM knowledge_attach WHERE knowledge_id=#{knowledgeId} AND business_state='1' AND doc_id IS NOT NULL")
    List<String> disabledDocIds(Long knowledgeId);
}
