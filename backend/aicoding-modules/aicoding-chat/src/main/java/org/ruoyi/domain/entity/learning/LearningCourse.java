package org.ruoyi.domain.entity.learning;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;
@Data @EqualsAndHashCode(callSuper=true) @TableName("learning_course")
public class LearningCourse extends TenantEntity {
    @TableId @JsonSerialize(using=ToStringSerializer.class) private Long id;
    private String title;
    private String description;
    private String chapters;
    private String materialIds;
    private String status;
    private Integer version;
}
