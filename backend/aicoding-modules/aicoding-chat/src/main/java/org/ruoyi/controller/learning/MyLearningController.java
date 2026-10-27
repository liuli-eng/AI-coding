package org.ruoyi.controller.learning;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.service.learning.LearningCourseService;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/learning")
public class MyLearningController {
    private final LearningCourseService service;
    @GetMapping("/courses") public TableDataInfo<LearningCourseService.CourseSummary> courses(String keyword,PageQuery page){LoginHelper.getUserId();return service.list(keyword,null,true,page);}
    @PostMapping("/courses/{id}/join") public R<String> join(@PathVariable Long id){return R.ok("加入成功",service.join(id,LoginHelper.getUserId()).toString());}
    @GetMapping("/mine") public TableDataInfo<LearningCourseService.EnrollmentSummary> mine(PageQuery page){return service.mine(LoginHelper.getUserId(),page);}
    @GetMapping("/mine/{id}") public R<LearningCourseService.EnrollmentDetail> detail(@PathVariable Long id){return R.ok(service.learning(id,LoginHelper.getUserId()));}
    public record Progress(@NotNull Boolean completed,@NotNull @Min(1) Integer version){}
    @PutMapping("/mine/{id}/chapters/{chapterId}") public R<Void> progress(@PathVariable Long id,@PathVariable String chapterId,@Valid @RequestBody Progress body){service.progress(id,chapterId,body.completed(),body.version(),LoginHelper.getUserId());return R.ok();}
    @PostMapping("/mine/{id}/chapters/{chapterId}/session") public R<LearningCourseService.SessionStart> start(@PathVariable Long id,@PathVariable String chapterId){return R.ok(service.startChapter(id,chapterId,LoginHelper.getUserId()));}
}
