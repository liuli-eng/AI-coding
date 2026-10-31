package org.ruoyi.controller.learning;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.service.learning.CourseMaterialService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("dev")
class CourseMaterialRequestTest {
    @Test void multipartFileContractReturnsStringIdAndSafeMetadata() throws Exception {
        var service=mock(CourseMaterialService.class);when(service.upload(any())).thenReturn(new CourseMaterialService.MaterialSummary("9007199254740993","教材.txt",12,"txt",4));
        var mvc=MockMvcBuilders.standaloneSetup(new CourseMaterialController(service)).build();
        mvc.perform(multipart("/system/course/materials").file(new MockMultipartFile("file","教材.txt","text/plain","正文".getBytes(java.nio.charset.StandardCharsets.UTF_8))).param("ossId","forged").param("url","forged"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("9007199254740993")).andExpect(jsonPath("$.data.textLength").value(4)).andExpect(jsonPath("$.data.ossId").doesNotExist()).andExpect(jsonPath("$.data.url").doesNotExist());
        var file=org.mockito.ArgumentCaptor.forClass(MultipartFile.class);verify(service).upload(file.capture());assertEquals("教材.txt",file.getValue().getOriginalFilename());
    }
    @Test void uploadAndDeleteUseEitherAdminPermissionAndDownloadRequiresQuery() throws Exception {
        for(String method:new String[]{"upload","delete"}) {
            var parameter=method.equals("upload")?MultipartFile.class:Long.class;
            var annotation=CourseMaterialController.class.getMethod(method,parameter).getAnnotation(SaCheckPermission.class);
            assertArrayEquals(new String[]{"learning:course:add","learning:course:edit"},annotation.value());assertEquals(SaMode.OR,annotation.mode());
        }
        assertArrayEquals(new String[]{"learning:course:query"},CourseMaterialController.class.getMethod("download",Long.class,jakarta.servlet.http.HttpServletResponse.class).getAnnotation(SaCheckPermission.class).value());
    }
}
