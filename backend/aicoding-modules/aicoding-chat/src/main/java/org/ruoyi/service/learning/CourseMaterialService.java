package org.ruoyi.service.learning;

import static org.ruoyi.service.learning.CourseMaterialValidationException.Reason.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.mapper.learning.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service @RequiredArgsConstructor
public class CourseMaterialService {
    public static final int MAX_FILE_SIZE=10*1024*1024;
    private final CourseMaterialMapper materials;
    private final CourseEnrollmentMapper enrollments;
    private final ChatSessionMapper sessions;
    private final OssService oss;
    private final CourseMaterialParser parser;
    private final LearningCourseMapper courses;
    public record MaterialSummary(String id,String name,long size,String suffix,int textLength) {}
    public record SessionMaterialContext(String materialIds,String learningContext) {}

    public MaterialSummary upload(MultipartFile file) {
        String tenant=CourseAccess.tenantId();Long user=CourseAccess.userId();
        if(file==null||file.isEmpty())throw new CourseMaterialValidationException(EMPTY_FILE);
        if(file.getSize()>MAX_FILE_SIZE)throw new CourseMaterialValidationException(FILE_TOO_LARGE);
        String name=Objects.toString(file.getOriginalFilename(),"").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        if(name.isBlank()||name.length()>255||name.chars().anyMatch(Character::isISOControl))throw new CourseMaterialValidationException(INVALID_FILENAME);
        int dot=name.lastIndexOf('.');String suffix=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!Set.of("pdf","docx","txt","md").contains(suffix))throw new CourseMaterialValidationException(UNSUPPORTED_TYPE);
        byte[] bytes;
        try(var input=file.getInputStream()){bytes=input.readNBytes(MAX_FILE_SIZE+1);}
        catch(IOException e){throw new CourseMaterialValidationException(UNREADABLE_UPLOAD);}
        if(bytes.length==0)throw new CourseMaterialValidationException(EMPTY_FILE);
        if(bytes.length>MAX_FILE_SIZE)throw new CourseMaterialValidationException(FILE_TOO_LARGE);
        String text=parser.parse(bytes,suffix); // No object exists until validation and parsing finish.
        var object=oss.uploadFile(file);
        if(object==null||object.getOssId()==null)throw new ServiceException("文件存储失败，请重试");
        var material=new LearningCourseMaterial();material.setTenantId(tenant);material.setCreateBy(user);material.setUpdateBy(user);
        material.setOssId(object.getOssId());material.setName(name);material.setSize((long)bytes.length);material.setSuffix(suffix);material.setTextContent(text);
        try {
            if(materials.insert(material)!=1)throw new ServiceException("资料保存失败，请重试");
        } catch(RuntimeException failure) {
            try{if(!Boolean.TRUE.equals(oss.deleteFile(object.getOssId())))failure.addSuppressed(new IllegalStateException("上传对象清理失败"));}
            catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;
        }
        return summary(material);
    }
    public static List<String> ids(String json) {
        if(json==null||json.isBlank())return List.of();
        List<String> ids=JsonUtils.parseArray(json,String.class);
        return ids==null?List.of():List.copyOf(ids);
    }
    private Long id(String value){
        try{if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();return Long.valueOf(value);}
        catch(NumberFormatException e){throw new CourseMaterialValidationException(INVALID_MATERIAL_ID);}
    }
    private LearningCourseMaterial require(String value,boolean lock) {
        return require(value,lock,CourseAccess.tenantId());
    }
    private LearningCourseMaterial require(String value,boolean lock,String tenant) {
        var query=Wrappers.<LearningCourseMaterial>lambdaQuery().eq(LearningCourseMaterial::getId,id(value)).eq(LearningCourseMaterial::getTenantId,tenant);
        if(lock)query.last("FOR UPDATE");
        var material=materials.selectOne(query);
        if(material==null||!tenant.equals(material.getTenantId()))throw new ServiceException("资料不存在或无权访问");
        return material;
    }
    /** Called inside the course save transaction; ordered locks also serialize competing deletions. */
    public List<String> validateBinding(List<String> requested,List<String> previous) {
        if(requested.size()>10)throw new CourseMaterialValidationException(TOO_MANY_MATERIALS);
        var unique=new LinkedHashSet<>(requested);
        if(unique.size()!=requested.size())throw new CourseMaterialValidationException(DUPLICATE_MATERIAL);
        Long user=CourseAccess.userId();long length=0;
        var sorted=new ArrayList<>(unique);sorted.forEach(this::id);sorted.sort(Comparator.comparingLong(this::id));
        for(String value:sorted) {
            var material=require(value,true);
            if(!previous.contains(value)&&(!user.equals(material.getCreateBy())||materials.referenceCount(value)>0))throw new ServiceException("只能绑定本人新上传或课程原有资料");
            length+=material.getTextContent().length();
            if(length>CourseMaterialParser.MAX_TEXT)throw new CourseMaterialValidationException(AGGREGATE_TEXT_TOO_LONG);
        }
        return List.copyOf(unique);
    }
    public void lockSnapshot(String snapshot){for(String value:ids(snapshot).stream().sorted(Comparator.comparingLong(this::id)).toList())require(value,true);}
    public List<MaterialSummary> summaries(String snapshot){return ids(snapshot).stream().map(value->summary(require(value,false))).toList();}
    public String context(String snapshot){
        return context(snapshot,CourseAccess.tenantId());
    }
    private String context(String snapshot,String tenant){
        var text=new StringBuilder();
        for(String value:ids(snapshot)){var material=require(value,false,tenant);text.append("\n\n课程资料：").append(material.getName()).append('\n').append(material.getTextContent());}
        return text.toString();
    }
    /** Attachments follow the current course; chapter and progress snapshots stay unchanged. */
    public String currentMaterialIds(CourseEnrollment enrollment) {
        return currentMaterialIds(enrollment,CourseAccess.tenantId());
    }
    private String currentMaterialIds(CourseEnrollment enrollment,String tenant) {
        if(!tenant.equals(enrollment.getTenantId()))throw new ServiceException("学习记录不存在或无权访问");
        if(enrollment.getCourseId()==null)return enrollment.getMaterialIds();
        var course=courses.selectOne(Wrappers.<LearningCourse>lambdaQuery().eq(LearningCourse::getId,enrollment.getCourseId()).eq(LearningCourse::getTenantId,tenant));
        // A removed course can still leave a historical learning record.
        if(course==null)return enrollment.getMaterialIds();
        if(!tenant.equals(course.getTenantId()))throw new ServiceException("课程不存在或无权访问");
        return course.getMaterialIds();
    }
    private MaterialSummary summary(LearningCourseMaterial m){return new MaterialSummary(m.getId().toString(),m.getName(),m.getSize(),m.getSuffix(),m.getTextContent().length());}
    public void downloadAdmin(Long materialId,HttpServletResponse response) throws IOException {download(materialId,response);}
    private void download(Long materialId,HttpServletResponse response) throws IOException {var m=require(materialId.toString(),false);oss.downloadFile(m.getOssId(),response);}
    private CourseEnrollment ownedEnrollment(Long enrollmentId){
        String tenant=CourseAccess.tenantId();Long user=CourseAccess.userId();
        var enrollment=enrollments.selectOne(Wrappers.<CourseEnrollment>lambdaQuery().eq(CourseEnrollment::getId,enrollmentId).eq(CourseEnrollment::getTenantId,tenant).eq(CourseEnrollment::getUserId,user));
        if(enrollment==null||!tenant.equals(enrollment.getTenantId())||!user.equals(enrollment.getUserId()))throw new ServiceException("学习记录不存在或无权访问");
        return enrollment;
    }
    private ChatSession ownedSession(Long sessionId){
        return ownedSession(sessionId,CourseAccess.tenantId(),CourseAccess.userId());
    }
    private ChatSession ownedSession(Long sessionId,String tenant,Long user){
        if(tenant==null||tenant.isBlank()||user==null||user<=0)throw new ServiceException("请先登录");
        var session=sessions.selectOne(Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getId,sessionId).eq(ChatSession::getTenantId,tenant).eq(ChatSession::getUserId,user));
        if(session==null||!CourseAccess.sameSessionTenant(tenant,session.getTenantId())||!user.equals(session.getUserId()))throw new ServiceException("会话不存在或无权访问");
        return session;
    }
    private CourseEnrollment sessionEnrollment(ChatSession session,String tenant,Long user) {
        if(!"learning".equals(session.getChatMode()))return null;
        var enrollment=enrollments.selectOne(Wrappers.<CourseEnrollment>lambdaQuery()
            .eq(CourseEnrollment::getTenantId,tenant).eq(CourseEnrollment::getUserId,user)
            .apply("JSON_CONTAINS(JSON_EXTRACT(COALESCE(chapter_sessions, '{}'), '$.*'), JSON_QUOTE({0}))",session.getId().toString()));
        if(enrollment!=null&&(!tenant.equals(enrollment.getTenantId())||!user.equals(enrollment.getUserId())))throw new ServiceException("学习记录不存在或无权访问");
        return enrollment;
    }
    private String sessionMaterialIds(ChatSession session,String tenant,Long user) {
        var enrollment=sessionEnrollment(session,tenant,user);
        return enrollment==null?session.getCourseMaterialIds():currentMaterialIds(enrollment,tenant);
    }
    /** Resolves on every turn, including old sessions created before any attachment existed. */
    public SessionMaterialContext resolveSessionContext(Long sessionId,Long user,String tenant) {
        var session=ownedSession(sessionId,tenant,user);
        var enrollment=sessionEnrollment(session,tenant,user);
        if(enrollment==null)return new SessionMaterialContext(session.getCourseMaterialIds(),session.getLearningContext());
        Map<String,String> links=JsonUtils.parseObject(enrollment.getChapterSessions(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){});
        String chapterId=links.entrySet().stream().filter(link->sessionId.toString().equals(link.getValue())).map(Map.Entry::getKey).findFirst().orElseThrow(()->new ServiceException("课程章节不存在"));
        var chapter=LearningCourseService.chapters(enrollment.getChapters()).stream().filter(c->chapterId.equals(c.id())).findFirst().orElseThrow(()->new ServiceException("课程章节不存在"));
        String current=currentMaterialIds(enrollment,tenant);
        String teaching="课程："+enrollment.getTitle()+"\n章节："+chapter.title()+"\n学习材料：\n"+chapter.content()+context(current,tenant);
        return new SessionMaterialContext(current,teaching);
    }
    private void contains(String snapshot,Long materialId){if(!ids(snapshot).contains(materialId.toString()))throw new ServiceException("资料不存在或无权访问");}
    public void downloadEnrollment(Long enrollmentId,Long materialId,HttpServletResponse response) throws IOException {var e=ownedEnrollment(enrollmentId);contains(currentMaterialIds(e),materialId);download(materialId,response);}
    public List<MaterialSummary> sessionMaterials(Long sessionId){var s=ownedSession(sessionId);return summaries(sessionMaterialIds(s,CourseAccess.tenantId(),CourseAccess.userId()));}
    public void downloadSession(Long sessionId,Long materialId,HttpServletResponse response) throws IOException {var s=ownedSession(sessionId);contains(sessionMaterialIds(s,CourseAccess.tenantId(),CourseAccess.userId()),materialId);download(materialId,response);}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void delete(Long materialId){
        var material=require(materialId.toString(),true);
        if(!CourseAccess.userId().equals(material.getCreateBy()))throw new ServiceException("只能删除本人上传的未引用资料");
        if(materials.referenceCount(materialId.toString())>0)throw new ServiceException("资料已被课程、学习记录或会话引用，不能删除");
        if(!Boolean.TRUE.equals(oss.deleteFile(material.getOssId())))throw new ServiceException("文件删除失败，请重试");
        if(materials.delete(Wrappers.<LearningCourseMaterial>lambdaQuery().eq(LearningCourseMaterial::getId,materialId).eq(LearningCourseMaterial::getTenantId,material.getTenantId()).eq(LearningCourseMaterial::getCreateBy,material.getCreateBy()))!=1)throw new ServiceException("资料删除失败，请重试");
    }
}
