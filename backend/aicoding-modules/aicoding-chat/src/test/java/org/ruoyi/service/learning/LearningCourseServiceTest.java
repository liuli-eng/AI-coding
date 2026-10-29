package org.ruoyi.service.learning;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.controller.learning.MyLearningController;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.service.chat.IChatSessionService;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class LearningCourseServiceTest {
    @BeforeAll static void setup() {
        var context = new GenericApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh(); new SpringUtil().setApplicationContext(context);
        for (var type : List.of(LearningCourse.class, CourseEnrollment.class, ChatSession.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "course-test"), type);
    }
    private final LearningCourseMapper courses = mock(LearningCourseMapper.class);
    private final CourseEnrollmentMapper enrollments = mock(CourseEnrollmentMapper.class);
    private final IChatSessionService sessions = mock(IChatSessionService.class);
    private final ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
    private final CourseMaterialService materials = mock(CourseMaterialService.class);
    private org.mockito.MockedStatic<LoginHelper> login;
    private org.mockito.MockedStatic<org.ruoyi.common.tenant.helper.TenantHelper> tenant;
    @BeforeEach void identity(){login=mockStatic(LoginHelper.class);tenant=mockStatic(org.ruoyi.common.tenant.helper.TenantHelper.class);login.when(LoginHelper::getUserId).thenReturn(1L);login.when(LoginHelper::getTenantId).thenReturn("000000");tenant.when(org.ruoyi.common.tenant.helper.TenantHelper::getTenantId).thenReturn("000000");}
    @AfterEach void closeIdentity(){tenant.close();login.close();}
    private final LearningCourseService service = new LearningCourseService(courses, enrollments, sessions, sessionMapper, materials);
    private LearningCourse course() {
        var c = new LearningCourse(); c.setTenantId("000000"); c.setId(10L); c.setTitle("Java 入门"); c.setDescription("从变量开始");
        c.setStatus("published"); c.setVersion(2);
        c.setChapters(JsonUtils.toJsonString(List.of(new CourseSave.Chapter("c1", "变量", "声明变量并观察值的变化。")))); return c;
    }
    private CourseEnrollment enrollment() {
        var e = new CourseEnrollment(); e.setTenantId("000000"); e.setId(20L); e.setUserId(1L); e.setCourseId(10L);
        e.setTitle(course().getTitle()); e.setChapters(course().getChapters()); e.setCourseVersion(2);
        e.setCompletedChapters("[]"); e.setChapterSessions("{}"); e.setVersion(1); return e;
    }
    @Test void publishedCoursesRequireChaptersAndUniqueStableChapterIds() {
        assertThrows(ServiceException.class, () -> service.save(null, new CourseSave("测试", "", "published", List.of(), null)));
        var duplicate = List.of(new CourseSave.Chapter("same", "一", "材料"), new CourseSave.Chapter("same", "二", "材料"));
        assertThrows(ServiceException.class, () -> service.save(null, new CourseSave("测试", "", "draft", duplicate, null)));
        when(courses.insert(any(LearningCourse.class))).thenAnswer(call -> {
            LearningCourse c = call.getArgument(0); c.setTenantId("000000"); c.setId(10L);
            var chapters = LearningCourseService.chapters(c.getChapters());
            assertFalse(chapters.get(0).id().isBlank()); assertEquals(1, c.getVersion()); return 1;
        });
        assertEquals(10L, service.save(null, new CourseSave("测试", "", "published", List.of(new CourseSave.Chapter(null, "一", "材料")), null)));
    }
    @Test void joiningCopiesCourseVersionAndRepeatedJoinPreservesSnapshot() {
        var c = course(); when(courses.selectOne(any(Wrapper.class))).thenReturn(c);
        when(enrollments.insert(any(CourseEnrollment.class))).thenAnswer(call -> {
            CourseEnrollment e = call.getArgument(0); e.setTenantId("000000"); e.setId(20L);
            assertEquals(c.getChapters(), e.getChapters()); assertEquals(2, e.getCourseVersion());
            assertEquals(1L, e.getUserId()); return 1;
        });
        assertEquals(20L, service.join(10L, 1L));
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(enrollment());
        c.setStatus("archived"); c.setChapters("[]"); c.setVersion(3);
        assertEquals(20L, service.join(10L, 1L));
        assertEquals(1, service.learning(20L, 1L).chapters().size());
        verify(enrollments, times(1)).insert(any(CourseEnrollment.class));
    }
    @Test void unpublishedCourseCannotBeJoinedAndConcurrentJoinReturnsExistingRecord() {
        var c = course(); c.setStatus("draft"); when(courses.selectOne(any(Wrapper.class))).thenReturn(c);
        assertThrows(ServiceException.class, () -> service.join(10L, 1L));
        c.setStatus("published");
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(null, enrollment());
        when(enrollments.insert(any(CourseEnrollment.class))).thenThrow(new DuplicateKeyException("race"));
        assertEquals(20L, service.join(10L, 1L));
    }
    @Test void progressRequiresOwnerExistingChapterAndMatchingVersion() {
        when(enrollments.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> w = call.getArgument(0);
            assertTrue(w.getSqlSegment().contains("user_id")); return null;
        });
        assertThrows(ServiceException.class, () -> service.progress(20L, "c1", true, 1, 99L));
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(enrollment());
        assertThrows(ServiceException.class, () -> service.progress(20L, "missing", true, 1, 1L));
        when(enrollments.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.progress(20L, "c1", true, 1, 1L));
        when(enrollments.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            var w = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) call.getArgument(1);
            assertTrue(w.getSqlSegment().contains("user_id")); assertTrue(w.getSqlSegment().contains("version"));
            assertTrue(w.getParamNameValuePairs().containsValue("[\"c1\"]")); return 1;
        });
        assertDoesNotThrow(() -> service.progress(20L, "c1", true, 1, 1L));
    }
    @Test void chapterSessionUsesSnapshotMaterialAndIsReusedWhenStillOwned() {
        var e = enrollment(); when(enrollments.selectOne(any(Wrapper.class))).thenReturn(e);
        when(sessions.insertByBo(any())).thenAnswer(call -> {org.ruoyi.domain.bo.chat.ChatSessionBo bo = call.getArgument(0); bo.setId(30L); assertEquals("learning", bo.getChatMode()); assertEquals(1L, bo.getUserId()); return true;});
        when(sessionMapper.updateById(any(ChatSession.class))).thenAnswer(call -> {
            ChatSession s = call.getArgument(0); assertTrue(s.getLearningContext().contains("声明变量")); return 1;
        });
        when(enrollments.update(isNull(), any(Wrapper.class))).thenReturn(1);
        var started = service.startChapter(20L, "c1", 1L); assertTrue(started.created()); assertEquals("30", started.sessionId());
        e.setChapterSessions("{\"c1\":\"30\"}"); var existing = new ChatSession(); existing.setTenantId("000000"); existing.setId(30L); existing.setUserId(1L);
        when(sessionMapper.selectOne(any(Wrapper.class))).thenAnswer(call->{Wrapper<?> query=call.getArgument(0);assertTrue(query.getSqlSegment().contains("tenant_id"));assertTrue(query.getSqlSegment().contains("user_id"));return existing;});
        assertFalse(service.startChapter(20L, "c1", 1L).created());
        verify(sessions, times(1)).insertByBo(any());
    }
    @Test void chapterSessionRejectsFailedWritesAndProgressConflict() {
        when(enrollments.selectOne(any(Wrapper.class))).thenReturn(enrollment());
        when(sessions.insertByBo(any())).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.startChapter(20L, "c1", 1L));
        when(sessions.insertByBo(any())).thenAnswer(call -> {org.ruoyi.domain.bo.chat.ChatSessionBo bo = call.getArgument(0); bo.setId(30L); return true;});
        when(sessionMapper.updateById(any(ChatSession.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.startChapter(20L, "c1", 1L));
        when(sessionMapper.updateById(any(ChatSession.class))).thenReturn(1);
        when(enrollments.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.startChapter(20L, "c1", 1L));
    }
    @Test void joinControllerReturnsEnrollmentIdInData() {
        var mockService = mock(LearningCourseService.class); when(mockService.join(10L, 1L)).thenReturn(20L);
        {
            assertEquals("20", new MyLearningController(mockService).join(10L).getData());
        }
    }
}
