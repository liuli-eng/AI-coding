package org.ruoyi.controller.chat;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.*;
import org.ruoyi.domain.bo.chat.ChatPromptSave;
import org.ruoyi.domain.entity.chat.ChatPrompt;
import org.ruoyi.service.chat.ChatPromptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequiredArgsConstructor @RequestMapping("/system/prompt")
public class ChatPromptController {
    private final ChatPromptService service;
    @GetMapping("/list") @SaCheckPermission("chat:prompt:list")
    public TableDataInfo<ChatPrompt> list(String keyword,String mode,String status,PageQuery page){return service.list(keyword,mode,status,page);}
    @GetMapping("/{id}") @SaCheckPermission("chat:prompt:query")
    public R<ChatPrompt> detail(@PathVariable Long id){return R.ok(service.detail(id));}
    @PostMapping @SaCheckPermission("chat:prompt:add") @Log(title="Prompt 管理",businessType=BusinessType.INSERT)
    public R<Long> add(@Valid @RequestBody ChatPromptSave body){return R.ok(service.save(null,body));}
    @PutMapping("/{id}") @SaCheckPermission("chat:prompt:edit") @Log(title="Prompt 管理",businessType=BusinessType.UPDATE)
    public R<Long> edit(@PathVariable Long id,@Valid @RequestBody ChatPromptSave body){return R.ok(service.save(id,body));}
    @DeleteMapping("/{id}") @SaCheckPermission("chat:prompt:remove") @Log(title="Prompt 管理",businessType=BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id,@RequestParam @Min(1) Integer version){service.remove(id,version);return R.ok();}
}
