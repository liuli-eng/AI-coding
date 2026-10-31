package org.ruoyi.controller.learning;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.learning.CourseMaterialService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController @RequiredArgsConstructor @RequestMapping("/system/course/materials")
public class CourseMaterialController {
    private final CourseMaterialService service;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission(value={"learning:course:add","learning:course:edit"},mode=SaMode.OR)
    public R<CourseMaterialService.MaterialSummary> upload(@RequestPart("file") MultipartFile file){return R.ok(service.upload(file));}
    @GetMapping("/{id}/download") @SaCheckPermission("learning:course:query")
    public void download(@PathVariable Long id,HttpServletResponse response) throws IOException {service.downloadAdmin(id,response);}
    @DeleteMapping("/{id}") @SaCheckPermission(value={"learning:course:add","learning:course:edit"},mode=SaMode.OR)
    public R<Void> delete(@PathVariable Long id){service.delete(id);return R.ok();}
}
