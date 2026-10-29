package org.ruoyi.controller.learning;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.log.annotation.Log;
import org.ruoyi.common.log.enums.BusinessType;
import org.ruoyi.common.mybatis.core.page.*;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.service.learning.LearningCourseService;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/system/course")
public class LearningCourseController {
    private final LearningCourseService service;
    @GetMapping("/list") @SaCheckPermission("learning:course:list")
    public TableDataInfo<LearningCourseService.CourseSummary> list(String keyword,String status,PageQuery page){return service.list(keyword,status,false,page);}
    @GetMapping("/{id}") @SaCheckPermission("learning:course:query")
    public R<LearningCourseService.CourseDetail> detail(@PathVariable Long id){return R.ok(service.detail(id));}
    @PostMapping @SaCheckPermission("learning:course:add") @Log(title="课程管理",businessType=BusinessType.INSERT)
    public R<String> add(@Valid @RequestBody CourseSave body){return R.ok("保存成功",service.save(null,body).toString());}
    @PutMapping("/{id}") @SaCheckPermission("learning:course:edit") @Log(title="课程管理",businessType=BusinessType.UPDATE)
    public R<String> edit(@PathVariable Long id,@Valid @RequestBody CourseSave body){return R.ok("保存成功",service.save(id,body).toString());}
}
