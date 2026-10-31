package org.ruoyi.domain.entity.learning;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;
@Data @EqualsAndHashCode(callSuper=true) @TableName("course_enrollment")
public class CourseEnrollment extends TenantEntity {
    @TableId @JsonSerialize(using=ToStringSerializer.class) private Long id;
    @JsonSerialize(using=ToStringSerializer.class) private Long courseId;
    @JsonSerialize(using=ToStringSerializer.class) private Long userId;
    private String title;
    private String description;
    private String chapters;
    private String materialIds;
    private Integer courseVersion;
    private String completedChapters;
    private String chapterSessions;
    private Integer version;
}
