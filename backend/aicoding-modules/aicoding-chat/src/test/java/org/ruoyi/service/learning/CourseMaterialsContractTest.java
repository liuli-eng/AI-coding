package org.ruoyi.service.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import cn.hutool.extra.spring.SpringUtil;
import org.springframework.context.support.GenericApplicationContext;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.learning.LearningCourse;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.service.chat.IChatSessionService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class CourseMaterialsContractTest {
    @BeforeAll static void jsonContext(){var context=new GenericApplicationContext();context.registerBean(ObjectMapper.class,()->new ObjectMapper());context.refresh();new SpringUtil().setApplicationContext(context);}
    @Test void oldCourseSaveConstructorStillSerializesAnOptionalMaterialBinding() throws Exception {
        var body = new CourseSave("课程", "", "draft", List.of(), null);
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(body));
        assertTrue(json.has("materialIds"), "CourseSave must expose the optional string-ID material binding");
        assertTrue(json.get("materialIds").isNull(), "Omitted materialIds must remain distinguishable from explicit []");
    }
    @Test void detailRejectsOtherTenantEvenWhenMapperReturnsIt() {
        var mapper = mock(LearningCourseMapper.class);
        var c = new LearningCourse(); c.setId(10L); c.setTenantId("other"); c.setChapters("[]");
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(c);
        var service = new LearningCourseService(mapper, mock(CourseEnrollmentMapper.class), mock(IChatSessionService.class), mock(ChatSessionMapper.class), mock(CourseMaterialService.class));
        try (var login=mockStatic(LoginHelper.class);var tenant=mockStatic(TenantHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(1L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            assertThrows(ServiceException.class, () -> service.detail(10L), "Optional tenant plugin must not be the access boundary");
        }
    }
}
