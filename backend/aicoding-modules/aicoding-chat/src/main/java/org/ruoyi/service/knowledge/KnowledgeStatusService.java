package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeInfo;
import org.ruoyi.mapper.knowledge.KnowledgeInfoMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeStatusService {
    private final KnowledgeInfoMapper mapper;

    public void changeStatus(Long id, String status) {
        if(id==null||id<=0||status==null||!Set.of("0","1").contains(status))throw new ServiceException("无效的知识库状态");
        if(!LoginHelper.isLogin())throw new ServiceException("请先登录");
        Long user=LoginHelper.getUserId();
        String tenant=TenantHelper.getTenantId();
        if(StringUtils.isBlank(tenant))tenant=LoginHelper.getTenantId();
        if(user==null||user<=0||StringUtils.isBlank(tenant))throw new ServiceException("请先登录");
        int updated=mapper.update(null,new UpdateWrapper<KnowledgeInfo>()
            .eq("id",id).eq("tenant_id",tenant).set("status",status)
            .set("update_by",user).set("update_time",new Date()));
        if(updated==0)throw new ServiceException("知识库不存在或无权操作");
    }

    /** Gate before cache reads and after retrieval so a disabled source cannot return cached text. */
    public void requireEnabled(String id) {
        Long parsed;
        try {
            if(id==null||!id.matches("[1-9][0-9]{0,18}"))throw new NumberFormatException();
            parsed=Long.valueOf(id);
        } catch(NumberFormatException invalid) { throw new ServiceException("知识库不可用"); }
        var info=mapper.selectById(parsed);
        if(info==null)throw new ServiceException("知识库不可用");
        requireEnabledStatus(info.getStatus());
    }

    public static void requireEnabledStatus(String status) {
        if(!"0".equals(status))throw new KnowledgeBaseDisabledException();
    }
}
