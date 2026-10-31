package org.ruoyi.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;

/** Server-owned immutable document. Only safe summaries cross the HTTP boundary. */
@Data @EqualsAndHashCode(callSuper=true) @TableName("learning_course_material")
public class LearningCourseMaterial extends TenantEntity {
    @TableId private Long id;
    private Long ossId;
    private String name;
    private Long size;
    private String suffix;
    private String textContent;
}
