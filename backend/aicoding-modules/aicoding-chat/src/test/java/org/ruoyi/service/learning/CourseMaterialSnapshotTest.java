package org.ruoyi.service.learning;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.service.chat.IChatSessionService;
import org.springframework.context.support.GenericApplicationContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class CourseMaterialSnapshotTest {
    @BeforeAll static void setup(){var context=new GenericApplicationContext();context.registerBean(ObjectMapper.class,()->new ObjectMapper());context.refresh();new SpringUtil().setApplicationContext(context);for(var type:List.of(LearningCourse.class,CourseEnrollment.class,ChatSession.class))TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"snapshot-test"),type);}
    private final LearningCourseMapper courses=mock(LearningCourseMapper.class);
    private final CourseEnrollmentMapper enrollments=mock(CourseEnrollmentMapper.class);
    private final IChatSessionService sessions=mock(IChatSessionService.class);
    private final ChatSessionMapper sessionMapper=mock(ChatSessionMapper.class);
    private final CourseMaterialService materials=mock(CourseMaterialService.class);
    private final LearningCourseService service=new LearningCourseService(courses,enrollments,sessions,sessionMapper,materials);
    private MockedStatic<LoginHelper> login;private MockedStatic<TenantHelper> tenant;
    @BeforeEach void identity(){login=mockStatic(LoginHelper.class);tenant=mockStatic(TenantHelper.class);login.when(LoginHelper::getUserId).thenReturn(1L);login.when(LoginHelper::getTenantId).thenReturn("000000");tenant.when(TenantHelper::getTenantId).thenReturn(null);when(materials.validateBinding(anyList(),anyList())).thenAnswer(c->c.getArgument(0));}
    @AfterEach void close(){tenant.close();login.close();}
    private LearningCourse course(){var c=new LearningCourse();c.setId(10L);c.setTenantId("000000");c.setTitle("课程");c.setStatus("published");c.setVersion(1);c.setChapters(JsonUtils.toJsonString(List.of(new CourseSave.Chapter("one","第一章","章节材料"))));return c;}
    @Test void omittedBindingsPreserveOnUpdateWhileExplicitEmptyClearsAndNewDefaultsEmpty(){
        var c=course();c.setMaterialIds("[\"9007199254740993\"]");when(courses.selectOne(any(Wrapper.class))).thenReturn(c);
        when(courses.update(isNull(),any(Wrapper.class))).thenAnswer(call->{LambdaUpdateWrapper<?> wrapper=call.getArgument(1);assertTrue(wrapper.getSqlSegment().contains("tenant_id"));assertTrue(wrapper.getParamNameValuePairs().containsValue(c.getMaterialIds()));return 1;});
        service.save(10L,new CourseSave("课程","","draft",List.of(),1));
        when(courses.update(isNull(),any(Wrapper.class))).thenAnswer(call->{LambdaUpdateWrapper<?> wrapper=call.getArgument(1);assertTrue(wrapper.getParamNameValuePairs().containsValue("[]"));return 1;});
        service.save(10L,new CourseSave("课程","","draft",List.of(),1,List.of()));
        when(courses.insert(any(LearningCourse.class))).thenAnswer(call->{LearningCourse created=call.getArgument(0);assertEquals("[]",created.getMaterialIds());created.setId(11L);return 1;});
        assertEquals(11L,service.save(null,new CourseSave("新课程","","draft",List.of(),null)));
    }
    @Test void startKeepsOriginalChaptersButUsesCurrentCourseAttachments(){
        var c=course();c.setMaterialIds("[\"9007199254740993\"]");when(courses.selectOne(any(Wrapper.class))).thenReturn(c);
        final CourseEnrollment[] saved=new CourseEnrollment[1];
        when(enrollments.insert(any(CourseEnrollment.class))).thenAnswer(call->{saved[0]=call.getArgument(0);saved[0].setId(20L);return 1;});
        assertEquals(20L,service.join(10L,1L));assertEquals("[\"9007199254740993\"]",saved[0].getMaterialIds());verify(materials).lockSnapshot(c.getMaterialIds());
        c.setMaterialIds("[]");c.setChapters("[]");when(enrollments.selectOne(any(Wrapper.class))).thenReturn(saved[0]);
        when(sessions.insertByBo(any())).thenAnswer(call->{org.ruoyi.domain.bo.chat.ChatSessionBo bo=call.getArgument(0);bo.setId(30L);return true;});
        when(materials.currentMaterialIds(saved[0])).thenReturn("[]");
        when(materials.context("[]")).thenReturn("");
        when(sessionMapper.updateById(any(ChatSession.class))).thenAnswer(call->{ChatSession created=call.getArgument(0);assertEquals("[]",created.getCourseMaterialIds());assertEquals("000000",created.getTenantId());assertTrue(created.getLearningContext().contains("章节材料"));assertFalse(created.getLearningContext().contains("上传的资料全文"));return 1;});
        when(enrollments.update(isNull(),any(Wrapper.class))).thenReturn(1);
        assertTrue(service.startChapter(20L,"one",1L).created());assertEquals("[\"9007199254740993\"]",saved[0].getMaterialIds());
    }
    @Test void historicalNullSnapshotsBecomeEmptyAndRemainCompatible(){assertEquals(List.of(),CourseMaterialService.ids(null));assertEquals(List.of(),CourseMaterialService.ids("[]"));}
}
