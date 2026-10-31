package org.ruoyi.service.learning;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class CourseMaterialServiceTest {
    @BeforeAll static void setup() {
        var context=new GenericApplicationContext();context.registerBean(ObjectMapper.class,() -> new ObjectMapper());context.refresh();new SpringUtil().setApplicationContext(context);
        for(var type:List.of(LearningCourseMaterial.class,LearningCourse.class,CourseEnrollment.class,ChatSession.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"material-test"),type);
    }
    private final CourseMaterialMapper materials=mock(CourseMaterialMapper.class);
    private final CourseEnrollmentMapper enrollments=mock(CourseEnrollmentMapper.class);
    private final ChatSessionMapper sessions=mock(ChatSessionMapper.class);
    private final OssService oss=mock(OssService.class);
    private final CourseMaterialService service=new CourseMaterialService(materials,enrollments,sessions,oss,new CourseMaterialParser(),mock(LearningCourseMapper.class));
    private MockedStatic<LoginHelper> login;private MockedStatic<TenantHelper> tenant;
    @BeforeEach void identity(){login=mockStatic(LoginHelper.class);tenant=mockStatic(TenantHelper.class);login.when(LoginHelper::getUserId).thenReturn(1L);login.when(LoginHelper::getTenantId).thenReturn("000000");tenant.when(TenantHelper::getTenantId).thenReturn(null);}
    @AfterEach void close(){tenant.close();login.close();}
    private LearningCourseMaterial material(){var m=new LearningCourseMaterial();m.setId(9007199254740993L);m.setTenantId("000000");m.setCreateBy(1L);m.setOssId(40L);m.setName("课程.txt");m.setSuffix("txt");m.setSize(12L);m.setTextContent("课程全文");return m;}
    private void found(){when(materials.selectOne(any(Wrapper.class))).thenReturn(material());}
    @Test void uploadUsesServerParsedTextAndReturnsOnlySafeSummary(){
        var object=new OssDTO();object.setOssId(40L);object.setOriginalName("untrusted");object.setUrl("secret");when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenReturn(object);
        when(materials.insert(any(LearningCourseMaterial.class))).thenAnswer(c->{LearningCourseMaterial m=c.getArgument(0);assertEquals("课程全文",m.getTextContent());assertEquals("000000",m.getTenantId());assertEquals(1L,m.getCreateBy());m.setId(9007199254740993L);return 1;});
        var result=service.upload(new MockMultipartFile("file","课程.TXT","application/forged","课程全文".getBytes(StandardCharsets.UTF_8)));
        assertEquals("9007199254740993",result.id());assertEquals("课程.TXT",result.name());assertEquals("txt",result.suffix());assertEquals(12,result.size());assertEquals(4,result.textLength());
        var json=new ObjectMapper().valueToTree(result);assertEquals(5,json.size());assertFalse(json.has("ossId"));assertFalse(json.has("textContent"));assertFalse(json.has("url"));
    }
    @Test void insertFailureCompensatesObjectAndPreservesOriginalError(){
        var object=new OssDTO();object.setOssId(40L);when(oss.uploadFile(any(org.springframework.web.multipart.MultipartFile.class))).thenReturn(object);
        when(materials.insert(any(LearningCourseMaterial.class))).thenThrow(new IllegalStateException("database"));
        assertThrows(IllegalStateException.class,()->service.upload(new MockMultipartFile("file","a.txt","text/plain","正文".getBytes(StandardCharsets.UTF_8))));verify(oss).deleteFile(40L);
    }
    @Test void invalidBlankOversizeAndOverlongUploadsNeverCreateAnObject(){
        for(var f:List.of(new MockMultipartFile("file","a.exe","text/plain",new byte[]{1}),new MockMultipartFile("file","a.txt","text/plain",new byte[0]),new MockMultipartFile("file","a.txt","text/plain"," \n ".getBytes(StandardCharsets.UTF_8)),new MockMultipartFile("file","a.txt","text/plain","x".repeat(60001).getBytes(StandardCharsets.UTF_8)),new MockMultipartFile("file","a.pdf","application/pdf","not PDF".getBytes(StandardCharsets.UTF_8))))assertThrows(CourseMaterialValidationException.class,()->service.upload(f));
        var oversized=spy(new MockMultipartFile("file","a.txt","text/plain",new byte[10*1024*1024+1]));doReturn(1L).when(oversized).getSize();assertThrows(CourseMaterialValidationException.class,()->service.upload(oversized));verifyNoInteractions(oss);
    }
    @Test void bindingRejectsForgedOtherOwnerCrossTenantDuplicateAndAggregateLimits(){
        found();var id="9007199254740993";assertEquals(List.of(id),service.validateBinding(List.of(id),List.of()));
        var other=material();other.setCreateBy(2L);when(materials.selectOne(any(Wrapper.class))).thenReturn(other);
        assertThrows(ServiceException.class,()->service.validateBinding(List.of(id),List.of()));assertEquals(List.of(id),service.validateBinding(List.of(id),List.of(id)));
        other.setTenantId("other");assertThrows(ServiceException.class,()->service.validateBinding(List.of(id),List.of(id)));
        assertThrows(CourseMaterialValidationException.class,()->service.validateBinding(List.of("bad"),List.of()));assertThrows(CourseMaterialValidationException.class,()->service.validateBinding(List.of(id,id),List.of()));
        assertThrows(CourseMaterialValidationException.class,()->service.validateBinding(java.util.stream.IntStream.range(0,11).mapToObj(Integer::toString).toList(),List.of()));
        var longText=material();longText.setTextContent("x".repeat(40000));when(materials.selectOne(any(Wrapper.class))).thenReturn(longText);
        assertThrows(CourseMaterialValidationException.class,()->service.validateBinding(List.of("1","2"),List.of()));
    }
    @Test void downloadsRequireTenantOwnerAndExactSnapshotMembershipBeforeOssAccess() throws Exception {
        found();var e=new CourseEnrollment();e.setId(20L);e.setTenantId("000000");e.setUserId(1L);e.setMaterialIds("[\"9007199254740993\"]");when(enrollments.selectOne(any(Wrapper.class))).thenReturn(e);
        var response=new MockHttpServletResponse();service.downloadEnrollment(20L,9007199254740993L,response);verify(oss).downloadFile(40L,response);reset(oss);
        e.setUserId(2L);assertThrows(ServiceException.class,()->service.downloadEnrollment(20L,9007199254740993L,response));e.setUserId(1L);e.setTenantId("other");assertThrows(ServiceException.class,()->service.downloadEnrollment(20L,9007199254740993L,response));e.setTenantId("000000");e.setMaterialIds("[]");assertThrows(ServiceException.class,()->service.downloadEnrollment(20L,9007199254740993L,response));verifyNoInteractions(oss);
    }
    @Test void sessionListAndDownloadUseOwnedTenantSnapshotAndNullMeansEmpty() throws Exception {
        found();var s=new ChatSession();s.setId(30L);s.setTenantId("0");s.setUserId(1L);when(sessions.selectOne(any(Wrapper.class))).thenReturn(s);
        assertEquals(List.of(),service.sessionMaterials(30L));s.setCourseMaterialIds("[\"9007199254740993\"]");assertEquals("9007199254740993",service.sessionMaterials(30L).get(0).id());
        var response=new MockHttpServletResponse();service.downloadSession(30L,9007199254740993L,response);verify(oss).downloadFile(40L,response);reset(oss);
        s.setUserId(2L);assertThrows(ServiceException.class,()->service.sessionMaterials(30L));assertThrows(ServiceException.class,()->service.downloadSession(30L,9007199254740993L,response));s.setUserId(1L);s.setTenantId("other");assertThrows(ServiceException.class,()->service.sessionMaterials(30L));verifyNoInteractions(oss);
    }
    @Test void adminDownloadChecksTenantAndDeleteRequiresUploaderAndZeroReferences() throws Exception {
        found();var response=new MockHttpServletResponse();service.downloadAdmin(9007199254740993L,response);verify(oss).downloadFile(40L,response);reset(oss);
        when(materials.referenceCount("9007199254740993")).thenReturn(1L);assertThrows(ServiceException.class,()->service.delete(9007199254740993L));verifyNoInteractions(oss);
        when(materials.referenceCount("9007199254740993")).thenReturn(0L);var other=material();other.setCreateBy(2L);when(materials.selectOne(any(Wrapper.class))).thenReturn(other);assertThrows(ServiceException.class,()->service.delete(other.getId()));verifyNoInteractions(oss);
        other.setCreateBy(1L);when(oss.deleteFile(40L)).thenReturn(true);when(materials.delete(any(Wrapper.class))).thenReturn(1);service.delete(other.getId());verify(oss).deleteFile(40L);
        other.setTenantId("other");reset(oss);assertThrows(ServiceException.class,()->service.downloadAdmin(other.getId(),response));assertThrows(ServiceException.class,()->service.delete(other.getId()));verifyNoInteractions(oss);
    }
    @Test void newBindingCannotBorrowAnAlreadyReferencedMaterialEvenFromSameUploader(){
        found();when(materials.referenceCount("9007199254740993")).thenReturn(1L);
        assertThrows(ServiceException.class,()->service.validateBinding(List.of("9007199254740993"),List.of()));
        assertEquals(List.of("9007199254740993"),service.validateBinding(List.of("9007199254740993"),List.of("9007199254740993")));
    }
    @Test void missingAuthenticatedTenantFailsClosed(){login.when(LoginHelper::getTenantId).thenReturn(null);assertThrows(ServiceException.class,()->service.sessionMaterials(30L));verifyNoInteractions(sessions,oss);}
}
