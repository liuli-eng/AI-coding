package org.ruoyi.service.learning;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.chat.IChatSessionService;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class CourseMaterialLiveUpdateTest {
    @BeforeAll static void setup() {
        var context = new GenericApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        for (var type : List.of(LearningCourseMaterial.class, LearningCourse.class, CourseEnrollment.class, ChatSession.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "live-materials"), type);
    }

    private final LearningCourseMapper courses = mock(LearningCourseMapper.class);
    private final CourseEnrollmentMapper enrollments = mock(CourseEnrollmentMapper.class);
    private final CourseMaterialMapper materialMapper = mock(CourseMaterialMapper.class);
    private final ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
    private final IChatSessionService sessions = mock(IChatSessionService.class);
    private final OssService oss = mock(OssService.class);
    private final CourseMaterialService materials = new CourseMaterialService(materialMapper, enrollments, sessionMapper, oss, new CourseMaterialParser(), courses);
    private final LearningCourseService learning = new LearningCourseService(courses, enrollments, sessions, sessionMapper, materials);
    private final ChatSessionOwnershipGuard guard = new ChatSessionOwnershipGuard(sessions, materials);
    private LearningCourse course;
    private CourseEnrollment enrollment;
    private ChatSession stored;
    private MockedStatic<LoginHelper> login;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach void identityAndRecords() {
        login = mockStatic(LoginHelper.class);
        tenant = mockStatic(TenantHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(1L);
        login.when(LoginHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::getTenantId).thenReturn(null);
        course = new LearningCourse();
        course.setId(10L); course.setTenantId("000000"); course.setTitle("后台修改后的标题");
        course.setMaterialIds("[\"9002\"]"); course.setChapters("[]");
        when(courses.selectOne(any(Wrapper.class))).thenReturn(course);
        enrollment = new CourseEnrollment();
        enrollment.setId(20L); enrollment.setTenantId("000000"); enrollment.setCourseId(10L); enrollment.setUserId(1L);
        enrollment.setTitle("原来的课程"); enrollment.setCourseVersion(1); enrollment.setVersion(7);
        enrollment.setChapters(JsonUtils.toJsonString(List.of(new CourseSave.Chapter("one", "原来的章节", "原来的章节正文"))));
        enrollment.setCompletedChapters("[\"one\"]"); enrollment.setChapterSessions("{\"one\":\"30\"}"); enrollment.setMaterialIds("[\"9001\"]");
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(enrollment);
        stored = new ChatSession();
        stored.setId(30L); stored.setTenantId("0"); stored.setUserId(1L); stored.setChatMode("learning");
        stored.setCourseMaterialIds("[\"9001\"]"); stored.setLearningContext("旧上下文\n\n课程资料：旧资料.txt\n过期的附件内容");
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        when(sessions.queryById(30L)).thenAnswer(call -> {
            var vo = new ChatSessionVo(); vo.setId(30L); vo.setUserId(1L); vo.setChatMode("learning");
            vo.setPromptSnapshot("原来的教学模板"); vo.setLearningContext(stored.getLearningContext());
            vo.setCourseMaterialIds(stored.getCourseMaterialIds()); return vo;
        });
        when(materialMapper.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) call.getArgument(0);
            query.getSqlSegment();
            boolean latest = query.getParamNameValuePairs().containsValue(9002L);
            var m = new LearningCourseMaterial(); m.setId(latest ? 9002L : 9001L); m.setTenantId("000000");
            m.setOssId(latest ? 42L : 41L); m.setSize(100L); m.setSuffix("txt");
            m.setName(latest ? "新资料.txt" : "旧资料.txt"); m.setTextContent(latest ? "示例项目叫青竹课堂" : "过期的附件内容");
            return m;
        });
    }

    @AfterEach void closeIdentity() { tenant.close(); login.close(); }

    @Test void enrollmentShowsLatestAttachmentsWithoutChangingChaptersOrProgress() {
        var detail = learning.learning(20L, 1L);
        assertEquals(List.of("9002"), detail.materials().stream().map(CourseMaterialService.MaterialSummary::id).toList());
        assertEquals("原来的章节正文", detail.chapters().get(0).content());
        assertEquals(List.of("one"), detail.completedChapters());
        assertEquals(7, detail.version()); assertEquals(1, detail.courseVersion());
        course.setMaterialIds("[]");
        assertTrue(learning.learning(20L, 1L).materials().isEmpty());
        verify(enrollments, never()).update(any(), any(Wrapper.class));
    }

    @Test void coursesJoinedBeforeFirstUploadAlsoReceiveNewAttachments() {
        enrollment.setMaterialIds(null); stored.setCourseMaterialIds(null);
        assertEquals("9002", learning.learning(20L, 1L).materials().get(0).id());
        assertEquals("9002", materials.sessionMaterials(30L).get(0).id());
    }

    @Test void existingSessionListsLatestAttachmentsAndClearsRemovedFiles() {
        assertEquals("9002", materials.sessionMaterials(30L).get(0).id());
        course.setMaterialIds("[]");
        assertTrue(materials.sessionMaterials(30L).isEmpty());
    }

    @Test void enrollmentAndSessionAuthorizeCurrentFileAndRejectRemovedFile() throws Exception {
        var response = new MockHttpServletResponse();
        materials.downloadEnrollment(20L, 9002L, response);
        materials.downloadSession(30L, 9002L, response);
        verify(oss, times(2)).downloadFile(42L, response);
        clearInvocations(oss);
        assertThrows(ServiceException.class, () -> materials.downloadEnrollment(20L, 9001L, response));
        assertThrows(ServiceException.class, () -> materials.downloadSession(30L, 9001L, response));
        course.setMaterialIds("[]");
        assertThrows(ServiceException.class, () -> materials.downloadSession(30L, 9002L, response));
        verifyNoInteractions(oss);
    }

    @Test void everyTurnRebuildsTeachingContextFromLatestFilesAndOriginalChapter() {
        var first = guard.requireOwned(1L, 30L);
        assertTrue(first.getLearningContext().contains("示例项目叫青竹课堂"));
        assertTrue(first.getLearningContext().contains("原来的章节正文"));
        assertFalse(first.getLearningContext().contains("过期的附件内容"));
        assertEquals("原来的教学模板", first.getPromptSnapshot());
        course.setMaterialIds("[]");
        var next = guard.requireOwned(1L, 30L);
        assertFalse(next.getLearningContext().contains("示例项目叫青竹课堂"));
        assertFalse(next.getLearningContext().contains("过期的附件内容"));
        assertTrue(next.getLearningContext().contains("原来的章节正文"));
    }

    @Test void anotherTenantCourseCannotSupplyLiveMaterials() {
        course.setTenantId("000001");
        assertThrows(ServiceException.class, () -> learning.learning(20L, 1L));
        assertThrows(ServiceException.class, () -> materials.sessionMaterials(30L));
        verifyNoInteractions(oss);
    }

    @Test void trustedWebSocketTenantWorksWithoutHttpLoginContext() {
        login.when(LoginHelper::getUserId).thenReturn(null);
        login.when(LoginHelper::getTenantId).thenReturn(null);
        var context = guard.requireOwned(1L, 30L, "000000");
        assertTrue(context.getLearningContext().contains("示例项目叫青竹课堂"));
        assertThrows(ServiceException.class, () -> guard.requireOwned(1L, 30L, "000001"));
    }

    @Test void unlinkedLearningSessionPreservesItsOwnContext() {
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals(stored.getLearningContext(), guard.requireOwned(1L, 30L).getLearningContext());
        verifyNoInteractions(courses);
    }

    @Test void sessionLookupAlwaysScopesItsEnrollmentToOwnerAndTenant() {
        when(enrollments.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("tenant_id"));
            assertTrue(query.getSqlSegment().contains("user_id"));
            assertTrue(query.getSqlSegment().contains("JSON_CONTAINS"));
            assertTrue(query.getParamNameValuePairs().containsValue("30"));
            return enrollment;
        });
        assertEquals("9002", materials.sessionMaterials(30L).get(0).id());
        enrollment.setUserId(2L);
        assertThrows(ServiceException.class, () -> materials.sessionMaterials(30L));
    }

    @Test void newChapterSessionAlsoUsesCurrentAttachmentsWithoutChangingProgress() {
        enrollment.setChapterSessions("{}");
        when(sessions.insertByBo(any())).thenAnswer(call -> {
            org.ruoyi.domain.bo.chat.ChatSessionBo bo = call.getArgument(0); bo.setId(31L); return true;
        });
        when(sessionMapper.updateById(any(ChatSession.class))).thenAnswer(call -> {
            ChatSession created = call.getArgument(0);
            assertEquals("[\"9002\"]", created.getCourseMaterialIds());
            assertTrue(created.getLearningContext().contains("示例项目叫青竹课堂"));
            assertTrue(created.getLearningContext().contains("原来的章节正文"));
            return 1;
        });
        when(enrollments.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertTrue(learning.startChapter(20L, "one", 1L).created());
        assertEquals("[\"one\"]", enrollment.getCompletedChapters());
        assertEquals("[\"9001\"]", enrollment.getMaterialIds());
    }
}
