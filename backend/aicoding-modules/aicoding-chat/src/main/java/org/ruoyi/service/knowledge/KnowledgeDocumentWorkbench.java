package org.ruoyi.service.knowledge;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.stereotype.Service;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import static org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.*;
@Service @RequiredArgsConstructor
public class KnowledgeDocumentWorkbench {
    private final KnowledgeAttachMapper mapper;
    private final KnowledgeAdminAccess access;
    private final OssService oss;
    private final KnowledgeRetrievalService retrieval;
    private final org.ruoyi.mapper.knowledge.KnowledgeFragmentMapper fragments;
    public KnowledgeDocumentPage list(KnowledgeAttachBo query,PageQuery pageQuery){
        validate(query);
        String tenant=KnowledgeSettingsService.tenant();
        int size=pageQuery.getPageSize()==null?20:Math.max(1,Math.min(100,pageQuery.getPageSize()));
        int number=pageQuery.getPageNum()==null?1:Math.max(1,pageQuery.getPageNum());
        var result=mapper.workbench(new Page<KnowledgeAttachVo>(number,size),tenant,query);
        return new KnowledgeDocumentPage(result.getRecords(),result.getTotal(),mapper.statistics(tenant,query));
    }
    public org.ruoyi.common.mybatis.core.page.TableDataInfo<KnowledgeOptionVo> knowledgeOptions(String keyword,PageQuery query){
        if(keyword!=null&&keyword.length()>100)throw new KnowledgeDocumentException(INVALID_SETTINGS);
        int size=query.getPageSize()==null?20:Math.max(1,Math.min(100,query.getPageSize()));
        int number=query.getPageNum()==null?1:Math.max(1,query.getPageNum());
        var rows=mapper.knowledgeOptions(new Page<KnowledgeOptionVo>(number,size),KnowledgeSettingsService.tenant(),keyword);
        return org.ruoyi.common.mybatis.core.page.TableDataInfo.build(rows);
    }
    private void validate(KnowledgeAttachBo q){
        if((q.getStatus()!=null&&(q.getStatus()<0||q.getStatus()>3))||(q.getBusinessState()!=null&&!java.util.Set.of("0","1").contains(q.getBusinessState()))||q.getName()!=null&&q.getName().length()>255)throw new KnowledgeDocumentException(INVALID_SETTINGS);
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void changeState(Long id,String state){
        var row=access.requireDocument(id,true);
        if(state==null||!java.util.Set.of("0","1").contains(state))throw new KnowledgeDocumentException(INVALID_SETTINGS);
        if(mapper.update(null,new UpdateWrapper<KnowledgeAttach>().eq("id",id).eq("tenant_id",KnowledgeSettingsService.tenant()).set("business_state",state).set("update_by",org.ruoyi.common.satoken.utils.LoginHelper.getUserId()).set("update_time",new java.util.Date()))!=1)throw new KnowledgeDocumentException(UNAVAILABLE);
        retrieval.invalidateKnowledge(String.valueOf(row.getKnowledgeId()));
    }
    public org.ruoyi.common.mybatis.core.page.TableDataInfo<KnowledgeFragmentVo> fragments(Long id,PageQuery query){
        var row=access.requireDocument(id);
        if(row.getDocId()==null||row.getDocId().isBlank())throw new KnowledgeDocumentException(UNAVAILABLE);
        int size=query.getPageSize()==null?10:Math.max(1,Math.min(100,query.getPageSize()));
        int number=query.getPageNum()==null?1:Math.max(1,query.getPageNum());
        var result=fragments.selectVoPage(new Page<org.ruoyi.domain.entity.knowledge.KnowledgeFragment>(number,size),com.baomidou.mybatisplus.core.toolkit.Wrappers.<org.ruoyi.domain.entity.knowledge.KnowledgeFragment>lambdaQuery()
            .eq(org.ruoyi.domain.entity.knowledge.KnowledgeFragment::getDocId,row.getDocId()).eq(org.ruoyi.domain.entity.knowledge.KnowledgeFragment::getKnowledgeId,row.getKnowledgeId()).orderByAsc(org.ruoyi.domain.entity.knowledge.KnowledgeFragment::getIdx));
        return org.ruoyi.common.mybatis.core.page.TableDataInfo.build(result);
    }
    public void download(Long id,HttpServletResponse response) throws java.io.IOException{
        var row=access.requireDocument(id);
        var objects=oss.selectByIds(String.valueOf(row.getOssId()));
        if(objects==null||objects.isEmpty())throw new KnowledgeDocumentException(UNAVAILABLE);
        var connection=new URL(objects.get(0).getUrl()).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(30000);
        try(var stream=connection.getInputStream()){
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(row.getName(),StandardCharsets.UTF_8).replace("+","%20"));
        stream.transferTo(response.getOutputStream());
        }catch(java.io.IOException failure){if(!response.isCommitted())response.reset();throw new KnowledgeDocumentException(READ_FAILED);}
    }
}
