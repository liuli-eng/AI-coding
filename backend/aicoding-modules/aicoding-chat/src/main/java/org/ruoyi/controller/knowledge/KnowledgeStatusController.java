package org.ruoyi.controller.knowledge;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.service.knowledge.KnowledgeStatusService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/info")
public class KnowledgeStatusController {
    private final KnowledgeStatusService service;
    public record Status(@NotNull @Pattern(regexp="[01]") String status) {}

    @SaCheckPermission("system:info:edit")
    @Log(title="知识库状态",businessType=BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public R<Void> change(@PathVariable @Positive Long id,@Valid @RequestBody Status body) {
        service.changeStatus(id,body.status());
        return R.ok();
    }
}
