package org.ruoyi.service.learning;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.common.mybatis.core.page.*;
import org.ruoyi.domain.bo.chat.ChatSessionBo;
import org.ruoyi.domain.bo.learning.CourseSave;
import org.ruoyi.domain.entity.learning.*;
import org.ruoyi.mapper.learning.*;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.ruoyi.service.chat.IChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class LearningCourseService {
    private final LearningCourseMapper courses;
    private final CourseEnrollmentMapper enrollments;
    private final IChatSessionService sessions;
    private final ChatSessionMapper sessionMapper;
    private final CourseMaterialService materials;

    public record CourseSummary(String id,String title,String description,String status,Integer version,int chapterCount) {}
    public record EnrollmentSummary(String id,String courseId,String title,String description,Integer courseVersion,int totalChapters,int completedCount,Integer version) {}
    public record EnrollmentDetail(String id,String courseId,String title,String description,Integer courseVersion,
        List<CourseSave.Chapter> chapters,List<String> completedChapters,Integer version,List<CourseMaterialService.MaterialSummary> materials) {}
    public record SessionStart(String sessionId,String initialMessage,boolean created) {}
    public record CourseDetail(String id,String title,String description,String status,Integer version,List<CourseSave.Chapter> chapters,List<CourseMaterialService.MaterialSummary> materials) {}

    public TableDataInfo<CourseSummary> list(String keyword,String status,boolean publishedOnly,PageQuery query) {
        var page=new Page<LearningCourse>(query.getPageNum()==null?1:Math.max(1,query.getPageNum()),query.getPageSize()==null?20:Math.min(100,Math.max(1,query.getPageSize())));
        courses.selectPage(page,Wrappers.<LearningCourse>lambdaQuery()
            .eq(LearningCourse::getTenantId,CourseAccess.tenantId())
            .like(StringUtils.isNotBlank(keyword),LearningCourse::getTitle,keyword)
            .eq(publishedOnly||StringUtils.isNotBlank(status),LearningCourse::getStatus,publishedOnly?"published":status)
            .orderByDesc(LearningCourse::getId));
        return new TableDataInfo<>(page.getRecords().stream().map(c->new CourseSummary(c.getId().toString(),c.getTitle(),c.getDescription(),c.getStatus(),c.getVersion(),chapters(c.getChapters()).size())).toList(),page.getTotal());
    }
    private LearningCourse require(Long id) {String tenant=CourseAccess.tenantId();var c=courses.selectOne(Wrappers.<LearningCourse>lambdaQuery().eq(LearningCourse::getId,id).eq(LearningCourse::getTenantId,tenant));if(c==null||!tenant.equals(c.getTenantId()))throw new ServiceException("课程不存在或无权访问");return c;}
    public CourseDetail detail(Long id) {var c=require(id);return new CourseDetail(c.getId().toString(),c.getTitle(),c.getDescription(),c.getStatus(),c.getVersion(),chapters(c.getChapters()),materials.summaries(c.getMaterialIds()));}
    public static List<CourseSave.Chapter> chapters(String json) {return JsonUtils.parseArray(json,CourseSave.Chapter.class);}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long save(Long id,CourseSave body) {
        String tenant=CourseAccess.tenantId();
        LearningCourse current=id==null?null:require(id);
        List<String> previous=current==null?List.of():CourseMaterialService.ids(current.getMaterialIds());
        List<String> selected=body.materialIds()==null?previous:body.materialIds();
        String materialJson=JsonUtils.toJsonString(materials.validateBinding(selected,previous));
        if(!Set.of("draft","published","archived").contains(body.status()))throw new ServiceException("无效的课程状态");
        if("published".equals(body.status())&&body.chapters().isEmpty())throw new ServiceException("发布前请至少添加一章");
        Set<String> ids=new HashSet<>();List<CourseSave.Chapter> normalized=new ArrayList<>();
        for(var chapter:body.chapters()) {
            String cid=StringUtils.isBlank(chapter.id())?UUID.randomUUID().toString():chapter.id();
            if(!ids.add(cid))throw new ServiceException("章节标识重复，请刷新后重试");
            normalized.add(new CourseSave.Chapter(cid,chapter.title().trim(),chapter.content().trim()));
        }
        String json=JsonUtils.toJsonString(normalized);
        if(id==null){var c=new LearningCourse();c.setTenantId(tenant);c.setMaterialIds(materialJson);c.setTitle(body.title().trim());c.setDescription(body.description());c.setStatus(body.status());c.setChapters(json);c.setVersion(1);courses.insert(c);return c.getId();}
        if(body.version()==null)throw new ServiceException("缺少课程版本，请刷新后重试");
        int updated=courses.update(null,Wrappers.<LearningCourse>lambdaUpdate().eq(LearningCourse::getId,id).eq(LearningCourse::getTenantId,tenant).eq(LearningCourse::getVersion,body.version())
            .set(LearningCourse::getTitle,body.title().trim()).set(LearningCourse::getDescription,body.description())
            .set(LearningCourse::getMaterialIds,materialJson).set(LearningCourse::getStatus,body.status()).set(LearningCourse::getChapters,json).set(LearningCourse::getVersion,body.version()+1));
        if(updated==0)throw new ServiceException("课程已更新，请刷新后重试");return id;
    }
    public TableDataInfo<EnrollmentSummary> mine(Long userId,PageQuery query) {
        requireUser(userId);
        var page=new Page<CourseEnrollment>(query.getPageNum()==null?1:Math.max(1,query.getPageNum()),query.getPageSize()==null?20:Math.min(100,Math.max(1,query.getPageSize())));
        enrollments.selectPage(page,Wrappers.<CourseEnrollment>lambdaQuery().eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId).orderByDesc(CourseEnrollment::getId));
        return new TableDataInfo<>(page.getRecords().stream().map(e->new EnrollmentSummary(e.getId().toString(),e.getCourseId().toString(),e.getTitle(),e.getDescription(),e.getCourseVersion(),chapters(e.getChapters()).size(),completed(e).size(),e.getVersion())).toList(),page.getTotal());
    }
    private void requireUser(Long id){if(id==null||id<=0||!id.equals(CourseAccess.userId()))throw new ServiceException("请先登录");}
    public CourseEnrollment owned(Long id,Long userId) {
        requireUser(userId);var e=enrollments.selectOne(Wrappers.<CourseEnrollment>lambdaQuery().eq(CourseEnrollment::getId,id).eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId));
        if(e==null||!CourseAccess.tenantId().equals(e.getTenantId())||!userId.equals(e.getUserId()))throw new ServiceException("学习记录不存在或无权访问");return e;
    }
    private List<String> completed(CourseEnrollment e){return JsonUtils.parseArray(e.getCompletedChapters(),String.class);}
    public EnrollmentDetail learning(Long id,Long userId){var e=owned(id,userId);return new EnrollmentDetail(e.getId().toString(),e.getCourseId().toString(),e.getTitle(),e.getDescription(),e.getCourseVersion(),chapters(e.getChapters()),completed(e),e.getVersion(),materials.summaries(materials.currentMaterialIds(e)));}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long join(Long courseId,Long userId) {
        requireUser(userId);
        var existing=enrollments.selectOne(Wrappers.<CourseEnrollment>lambdaQuery().eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId).eq(CourseEnrollment::getCourseId,courseId));
        if(existing!=null){if(!CourseAccess.tenantId().equals(existing.getTenantId())||!userId.equals(existing.getUserId()))throw new ServiceException("学习记录不存在或无权访问");return existing.getId();}
        var c=require(courseId);if(!"published".equals(c.getStatus()))throw new ServiceException("课程已下架，请刷新后重试");
        materials.lockSnapshot(c.getMaterialIds());
        var e=new CourseEnrollment();e.setTenantId(CourseAccess.tenantId());e.setMaterialIds(JsonUtils.toJsonString(CourseMaterialService.ids(c.getMaterialIds())));e.setUserId(userId);e.setCourseId(courseId);e.setTitle(c.getTitle());e.setDescription(c.getDescription());
        e.setCourseVersion(c.getVersion());e.setChapters(c.getChapters());e.setCompletedChapters("[]");e.setChapterSessions("{}");e.setVersion(1);
        try{enrollments.insert(e);}catch(org.springframework.dao.DuplicateKeyException duplicate){
            var joined=enrollments.selectOne(Wrappers.<CourseEnrollment>lambdaQuery().eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId).eq(CourseEnrollment::getCourseId,courseId));
            if(joined==null||!CourseAccess.tenantId().equals(joined.getTenantId())||!userId.equals(joined.getUserId()))throw duplicate;return joined.getId();
        }
        return e.getId();
    }
    public void progress(Long id,String chapterId,boolean done,Integer version,Long userId) {
        var e=owned(id,userId);requireChapter(e,chapterId);
        if(version==null)throw new ServiceException("缺少学习进度版本，请刷新后重试");
        var ids=new LinkedHashSet<>(completed(e));if(done)ids.add(chapterId);else ids.remove(chapterId);
        int updated=enrollments.update(null,Wrappers.<CourseEnrollment>lambdaUpdate().eq(CourseEnrollment::getId,id).eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId).eq(CourseEnrollment::getVersion,version)
            .set(CourseEnrollment::getCompletedChapters,JsonUtils.toJsonString(ids)).set(CourseEnrollment::getVersion,version+1));
        if(updated==0)throw new ServiceException("学习记录已更新，请刷新后重试");
    }
    private CourseSave.Chapter requireChapter(CourseEnrollment e,String chapterId){return chapters(e.getChapters()).stream().filter(c->c.id().equals(chapterId)).findFirst().orElseThrow(()->new ServiceException("章节不存在"));}
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SessionStart startChapter(Long id,String chapterId,Long userId) {
        var e=owned(id,userId);var chapter=requireChapter(e,chapterId);
        Map<String,String> links=JsonUtils.parseObject(e.getChapterSessions(),new TypeReference<Map<String,String>>(){});
        String linked=links.get(chapterId);
        if(linked!=null){var old=sessionMapper.selectOne(Wrappers.<org.ruoyi.domain.entity.chat.ChatSession>lambdaQuery().eq(org.ruoyi.domain.entity.chat.ChatSession::getId,Long.valueOf(linked)).eq(org.ruoyi.domain.entity.chat.ChatSession::getTenantId,CourseAccess.tenantId()).eq(org.ruoyi.domain.entity.chat.ChatSession::getUserId,userId));if(old!=null&&userId.equals(old.getUserId())&&CourseAccess.sameSessionTenant(CourseAccess.tenantId(),old.getTenantId()))return new SessionStart(linked,null,false);}
        var bo=new ChatSessionBo();bo.setUserId(userId);bo.setChatMode("learning");String sessionTitle=e.getTitle()+" · "+chapter.title();bo.setSessionTitle(sessionTitle.substring(0,Math.min(200,sessionTitle.length())));
        String initial="请带我学习《"+e.getTitle()+"》中的“"+chapter.title()+"”，先了解我的基础，再循序渐进讲解。";
        bo.setSessionContent(initial);if(!sessions.insertByBo(bo))throw new ServiceException("教学会话创建失败，请重试");
        var context=new org.ruoyi.domain.entity.chat.ChatSession();context.setId(bo.getId());
        String currentMaterials=materials.currentMaterialIds(e);
        context.setTenantId(CourseAccess.tenantId());context.setCourseMaterialIds(JsonUtils.toJsonString(CourseMaterialService.ids(currentMaterials)));
        context.setLearningContext("课程："+e.getTitle()+"\n章节："+chapter.title()+"\n学习材料：\n"+chapter.content()+materials.context(currentMaterials));if(sessionMapper.updateById(context)==0)throw new ServiceException("教学材料保存失败，请重试");
        links.put(chapterId,bo.getId().toString());
        int updated=enrollments.update(null,Wrappers.<CourseEnrollment>lambdaUpdate().eq(CourseEnrollment::getId,id).eq(CourseEnrollment::getTenantId,CourseAccess.tenantId()).eq(CourseEnrollment::getUserId,userId).eq(CourseEnrollment::getVersion,e.getVersion())
            .set(CourseEnrollment::getChapterSessions,JsonUtils.toJsonString(links)).set(CourseEnrollment::getVersion,e.getVersion()+1));
        if(updated==0)throw new ServiceException("学习记录已更新，请重试");
        return new SessionStart(bo.getId().toString(),initial,true);
    }
}
