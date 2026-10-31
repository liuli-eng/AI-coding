package org.ruoyi.controller.learning;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.common.web.handler.GlobalExceptionHandler;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.learning.LearningCourse;
import org.ruoyi.domain.entity.learning.LearningCourseMaterial;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.service.chat.IChatSessionService;
import org.ruoyi.service.learning.CourseMaterialParser;
import org.ruoyi.service.learning.CourseMaterialService;
import org.ruoyi.service.learning.LearningCourseService;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real controllers, parser, validation, and ordered HTTP advice. */
@Tag("dev")
class CourseMaterialValidationHttpTest {
    @Configuration
    @Import(GlobalExceptionHandler.class)
    @ComponentScan(basePackageClasses=CourseMaterialController.class, useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(RestControllerAdvice.class))
    static class AdviceConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    private static AnnotationConfigApplicationContext context;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CourseMaterialMapper materials = mock(CourseMaterialMapper.class);
    private final LearningCourseMapper courses = mock(LearningCourseMapper.class);
    private final OssService oss = mock(OssService.class);
    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;
    private MockMvc mvc;

    @BeforeAll static void setupContext() {
        context = new AnnotationConfigApplicationContext(AdviceConfiguration.class);
        new SpringUtil().setApplicationContext(context);
        for (var type : List.of(LearningCourse.class, LearningCourseMaterial.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "http-materials"), type);
    }

    @AfterAll static void closeContext() { context.close(); }

    @BeforeEach void setup() {
        login = mockStatic(LoginHelper.class);
        tenant = mockStatic(TenantHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(1L);
        login.when(LoginHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::getTenantId).thenReturn(null);
        var enrollments = mock(CourseEnrollmentMapper.class);
        var sessions = mock(ChatSessionMapper.class);
        var service = new CourseMaterialService(materials, enrollments, sessions, oss, new CourseMaterialParser(), courses);
        var courseService = new LearningCourseService(courses, enrollments, mock(IChatSessionService.class), sessions, service);
        var current = new LearningCourse();
        current.setId(10L); current.setTenantId("000000");
        when(courses.selectOne(any(Wrapper.class))).thenReturn(current);
        mvc = MockMvcBuilders.standaloneSetup(new CourseMaterialController(service), new LearningCourseController(courseService))
            .setControllerAdvice(context.getBeansWithAnnotation(RestControllerAdvice.class).values().toArray()).build();
    }

    @AfterEach void closeIdentity() { tenant.close(); login.close(); }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    static Stream<Arguments> invalidUploads() throws Exception {
        byte[] scanned;
        byte[] tooManyPages;
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage()); pdf.save(out); scanned = out.toByteArray();
            for (int i = 1; i <= 1000; i++) pdf.addPage(new PDPage());
            out.reset(); pdf.save(out); tooManyPages = out.toByteArray();
        }
        byte[] tooManyEntries;
        try (var out = new ByteArrayOutputStream(); var zip = new ZipOutputStream(out)) {
            for (int i = 0; i <= 1000; i++) { zip.putNextEntry(new ZipEntry("part" + i)); zip.closeEntry(); }
            zip.finish(); tooManyEntries = out.toByteArray();
        }
        byte[] expandedArchive;
        try (var out = new ByteArrayOutputStream(); var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("document.xml")); zip.write(new byte[30 * 1024 * 1024 + 1]);
            zip.closeEntry(); zip.finish(); expandedArchive = out.toByteArray();
        }
        return Stream.of(
            Arguments.of(file("empty.txt", new byte[0]), "文件为空，请选择包含文字的文件"),
            Arguments.of(file("blank.txt", " \n ".getBytes(StandardCharsets.UTF_8)), "文件没有可提取的文字，扫描件请先进行 OCR 文字识别"),
            Arguments.of(file("scanned.pdf", scanned), "文件没有可提取的文字，扫描件请先进行 OCR 文字识别"),
            Arguments.of(file("broken.pdf", new byte[]{1}), "文件无法解析，请确认文件未损坏且格式正确；TXT/MD 请使用 UTF-8 编码"),
            Arguments.of(file("broken.docx", new byte[]{1}), "文件无法解析，请确认文件未损坏且格式正确；TXT/MD 请使用 UTF-8 编码"),
            Arguments.of(file("encoding.md", new byte[]{(byte) 0xff}), "文件无法解析，请确认文件未损坏且格式正确；TXT/MD 请使用 UTF-8 编码"),
            Arguments.of(file("binary.txt", new byte[]{'x', 0}), "文本文件包含二进制内容，请上传 UTF-8 文本"),
            Arguments.of(file("bad.exe", new byte[]{1}), "仅支持 PDF、DOCX、TXT、MD 文件"),
            Arguments.of(file("x".repeat(252) + ".txt", new byte[]{1}), "文件名无效或超过255个字符"),
            Arguments.of(file("large.txt", new byte[10 * 1024 * 1024 + 1]), "单文件不能超过10MB"),
            Arguments.of(file("long.txt", "x".repeat(60001).getBytes(StandardCharsets.UTF_8)), "文件文字超过60000个字符，请拆分后上传"),
            Arguments.of(file("pages.pdf", tooManyPages), "PDF 页数超过1000页，请拆分后上传"),
            Arguments.of(file("entries.docx", tooManyEntries), "DOCX 文件结构过大，请拆分后上传"),
            Arguments.of(file("expanded.docx", expandedArchive), "DOCX 解压内容超过30MB，请拆分后上传")
        );
    }

    @ParameterizedTest @MethodSource("invalidUploads")
    void uploadValidationRetainsOnlyFixedActionableGuidance(MockMultipartFile file, String message) throws Exception {
        mvc.perform(multipart("/system/course/materials").file(file)).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500)).andExpect(jsonPath("$.msg").value(message));
        verifyNoInteractions(oss, materials);
    }

    @ParameterizedTest @ValueSource(strings={"POST", "PUT"})
    void elevenMaterialIdsReachTheSameControlledLimitForAddAndEdit(String method) throws Exception {
        var body = new CourseSave("课程", "", "draft", List.of(), 1,
            IntStream.rangeClosed(1, 11).mapToObj(Integer::toString).toList());
        mvc.perform((method.equals("POST") ? post("/system/course") : put("/system/course/10"))
            .contentType("application/json").content(JSON.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("每门课程最多10个文件"));
        verifyNoInteractions(oss, materials);
        verify(courses, never()).insert(any(LearningCourse.class));
    }

    @Test void aggregateLimitReachesCourseSaveResponse() throws Exception {
        var material = new LearningCourseMaterial();
        material.setTenantId("000000"); material.setCreateBy(1L); material.setTextContent("x".repeat(40000));
        when(materials.selectOne(any(Wrapper.class))).thenReturn(material);
        var body = new CourseSave("课程", "", "draft", List.of(), 1, List.of("1", "2"));
        mvc.perform(post("/system/course").contentType("application/json").content(JSON.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("课程资料合计不能超过60000个文字字符"));
        verify(courses, never()).insert(any(LearningCourse.class));
        verifyNoInteractions(oss);
    }

    @Test void unrelatedBeanValidationRemainsGeneric() throws Exception {
        var body = new CourseSave("", "", "draft", List.of(), 1, List.of());
        mvc.perform(post("/system/course").contentType("application/json").content(JSON.writeValueAsBytes(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("请求参数校验失败"));
        verifyNoInteractions(oss, materials);
    }

    @Test void ordinaryStorageServiceErrorRemainsGeneric() throws Exception {
        when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class)))
            .thenThrow(new ServiceException("private storage credential sentinel"));
        mvc.perform(multipart("/system/course/materials").file(file("lesson.txt", new byte[]{'x'})))
            .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("请求处理失败"));
    }

    @Test void databaseRuntimeErrorRemainsGeneric() throws Exception {
        var object = new OssDTO(); object.setOssId(40L);
        when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenReturn(object);
        when(materials.insert(any(LearningCourseMaterial.class))).thenThrow(new IllegalStateException("private database sentinel"));
        mvc.perform(multipart("/system/course/materials").file(file("lesson.txt", new byte[]{'x'})))
            .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("系统异常，请联系管理员"));
        verify(oss).deleteFile(40L);
    }
}
