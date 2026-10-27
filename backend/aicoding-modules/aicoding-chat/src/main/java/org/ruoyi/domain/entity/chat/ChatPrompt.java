package org.ruoyi.domain.entity.chat;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ruoyi.common.tenant.core.TenantEntity;
@Data @EqualsAndHashCode(callSuper=true) @TableName("chat_prompt")
public class ChatPrompt extends TenantEntity {
    @TableId @JsonSerialize(using=ToStringSerializer.class) private Long id;
    private String name;
    private String description;
    private String chatMode;
    private String content;
    private String status;
    private Integer version;
}
