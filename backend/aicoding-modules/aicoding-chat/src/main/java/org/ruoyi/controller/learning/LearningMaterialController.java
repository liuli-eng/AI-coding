package org.ruoyi.controller.learning;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.learning.CourseMaterialService;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/learning")
public class LearningMaterialController {
    private final CourseMaterialService service;
    @GetMapping("/mine/{enrollmentId}/materials/{materialId}/download")
    public void downloadEnrollment(@PathVariable Long enrollmentId,@PathVariable Long materialId,HttpServletResponse response) throws IOException {service.downloadEnrollment(enrollmentId,materialId,response);}
    @GetMapping("/sessions/{sessionId}/materials")
    public R<List<CourseMaterialService.MaterialSummary>> sessionMaterials(@PathVariable Long sessionId){return R.ok(service.sessionMaterials(sessionId));}
    @GetMapping("/sessions/{sessionId}/materials/{materialId}/download")
    public void downloadSession(@PathVariable Long sessionId,@PathVariable Long materialId,HttpServletResponse response) throws IOException {service.downloadSession(sessionId,materialId,response);}
}
