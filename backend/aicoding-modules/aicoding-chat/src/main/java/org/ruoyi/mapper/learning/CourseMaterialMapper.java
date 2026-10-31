package org.ruoyi.mapper.learning;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.ruoyi.domain.entity.learning.LearningCourseMaterial;

public interface CourseMaterialMapper extends BaseMapper<LearningCourseMaterial> {
    // Global reference check intentionally includes every tenant. Never delete a referenced object.
    @InterceptorIgnore(tenantLine="true")
    @Select("""
        SELECT (SELECT COUNT(*) FROM learning_course WHERE JSON_CONTAINS(COALESCE(material_ids, '[]'), JSON_QUOTE(#{id})))
             + (SELECT COUNT(*) FROM course_enrollment WHERE JSON_CONTAINS(COALESCE(material_ids, '[]'), JSON_QUOTE(#{id})))
             + (SELECT COUNT(*) FROM chat_session WHERE JSON_CONTAINS(COALESCE(course_material_ids, '[]'), JSON_QUOTE(#{id})))
        """)
    long referenceCount(@Param("id") String id);
}
