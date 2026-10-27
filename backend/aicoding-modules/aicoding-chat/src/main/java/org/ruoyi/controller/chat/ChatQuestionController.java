package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.chat.ChatQuestionQuery;
import org.ruoyi.domain.entity.chat.ChatQuestion;
import org.ruoyi.service.chat.ChatQuestionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/system/question")
public class ChatQuestionController {
    private final ChatQuestionService service;

    @SaCheckPermission("chat:question:list")
    @GetMapping("/list")
    public TableDataInfo<ChatQuestion> list(ChatQuestionQuery query, PageQuery page) { return service.page(query, page); }

    @SaCheckPermission("chat:question:query")
    @GetMapping("/{id}")
    public R<ChatQuestion> detail(@PathVariable Long id) { return R.ok(service.detail(id)); }

    @SaCheckPermission("chat:question:review")
    @Log(title = "问答复核", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/review")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody Review body) {
        service.review(id, body.status(), body.note(), body.version());
        return R.ok();
    }

    @SaCheckPermission("chat:overview:query")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() { return R.ok(service.overview()); }

    public record Review(@NotBlank String status, @Size(max = 2000) String note, @NotNull @Min(0) Integer version) {}
}
