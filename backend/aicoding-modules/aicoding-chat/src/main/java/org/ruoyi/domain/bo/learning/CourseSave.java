package org.ruoyi.domain.bo.learning;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CourseSave(@NotBlank @Size(max=150) String title, @Size(max=2000) String description,
    @NotBlank String status, @NotNull @Size(max=100) List<@Valid Chapter> chapters, @Min(1) Integer version, List<@NotBlank String> materialIds) {
    public CourseSave(String title,String description,String status,List<Chapter> chapters,Integer version) {
        this(title,description,status,chapters,version,null);
    }
    public record Chapter(@Size(max=64) String id, @NotBlank @Size(max=150) String title,
        @NotBlank @Size(max=12000) String content) {}
}
