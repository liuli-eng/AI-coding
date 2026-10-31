package org.ruoyi.controller.knowledge;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.domain.vo.knowledge.KnowledgeSettingsVo;
import org.ruoyi.service.knowledge.KnowledgeSettingsService;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/system/knowledge-settings")
public class KnowledgeSettingsController {
    private final KnowledgeSettingsService service;
    @GetMapping @SaCheckPermission("knowledge:settings:query")
    public R<KnowledgeSettingsVo> get(){return R.ok(service.current());}
    @PutMapping @SaCheckPermission("knowledge:settings:edit") @Log(title="知识库系统设置",businessType=BusinessType.UPDATE)
    public R<KnowledgeSettingsVo> save(@RequestBody KnowledgeSettingsVo body){return R.ok(service.save(body));}
    public record Reset(int version){}
    @PostMapping("/defaults") @SaCheckPermission("knowledge:settings:edit") @Log(title="恢复知识库默认设置",businessType=BusinessType.UPDATE)
    public R<KnowledgeSettingsVo> reset(@RequestBody Reset body){return R.ok(service.reset(body.version()));}
}
