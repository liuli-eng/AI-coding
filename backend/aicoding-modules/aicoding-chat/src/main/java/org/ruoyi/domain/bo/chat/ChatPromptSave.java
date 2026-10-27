package org.ruoyi.domain.bo.chat;
import jakarta.validation.constraints.*;
public record ChatPromptSave(@NotBlank @Size(max=100) String name,
    @Size(max=500) String description, @NotBlank String chatMode,
    @NotBlank @Size(max=16000) String content, @NotBlank String status,
    @Min(0) Integer version) {}
