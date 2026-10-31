package org.ruoyi.controller.knowledge;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.knowledge.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeDocumentWorkbench workbench;
    private final KnowledgeSettingsService settings;
    @GetMapping("/system/attach/workbench") @SaCheckPermission("system:attach:list")
    public R<KnowledgeDocumentPage> list(KnowledgeAttachBo query,PageQuery page){return R.ok(workbench.list(query,page));}
    @GetMapping("/system/attach/knowledge-options")
    @SaCheckPermission(value={"system:attach:list","system:attach:add","system:attach:edit"},mode=cn.dev33.satoken.annotation.SaMode.OR)
    public org.ruoyi.common.mybatis.core.page.TableDataInfo<KnowledgeOptionVo> options(String keyword,PageQuery page){return workbench.knowledgeOptions(keyword,page);}
    public record State(String businessState){}
    @PutMapping("/system/attach/{id}/state") @SaCheckPermission("system:attach:edit") @Log(title="文档启停",businessType=BusinessType.UPDATE)
    public R<Void> state(@PathVariable Long id,@RequestBody State state){workbench.changeState(id,state.businessState());return R.ok();}
    @GetMapping("/system/attach/{id}/download") @SaCheckPermission("system:attach:query")
    public void download(@PathVariable Long id,HttpServletResponse response) throws java.io.IOException{workbench.download(id,response);}
    @GetMapping("/system/attach/{id}/fragments") @SaCheckPermission("system:fragment:list")
    public org.ruoyi.common.mybatis.core.page.TableDataInfo<KnowledgeFragmentVo> fragments(@PathVariable Long id,PageQuery page){return workbench.fragments(id,page);}
    @GetMapping("/system/attach/upload-rules") @SaCheckPermission("system:attach:add")
    public R<KnowledgeSettingsVo> rules(){return R.ok(settings.current());}
    public record KnowledgeDefaults(Double similarityThreshold){}
    @GetMapping("/system/info/defaults") @SaCheckPermission("system:info:add")
    public R<KnowledgeDefaults> defaults(){return R.ok(new KnowledgeDefaults(settings.current().similarityThreshold()));}
    public record Preferences(String systemName){}
    @GetMapping("/knowledge/preferences")
    public R<Preferences> preferences(){return R.ok(new Preferences(settings.current().systemName()));}
}
