package org.ruoyi.service.chat;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.mybatis.core.page.PageQuery;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.chat.ChatPromptSave;
import org.ruoyi.domain.entity.chat.ChatPrompt;
import org.ruoyi.mapper.chat.ChatPromptMapper;
import org.springframework.stereotype.Service;
import java.util.List;
@Service @RequiredArgsConstructor
public class ChatPromptService {
    private final ChatPromptMapper mapper;
    public TableDataInfo<ChatPrompt> list(String keyword, String mode, String status, PageQuery query) {
        var page = new Page<ChatPrompt>(query.getPageNum()==null?1:Math.max(1,query.getPageNum()),
            query.getPageSize()==null?20:Math.min(100,Math.max(1,query.getPageSize())));
        return TableDataInfo.build(mapper.selectPage(page,Wrappers.<ChatPrompt>lambdaQuery()
            .select(ChatPrompt.class,f->!"content".equals(f.getProperty()))
            .like(StringUtils.isNotBlank(keyword),ChatPrompt::getName,keyword)
            .eq(StringUtils.isNotBlank(mode),ChatPrompt::getChatMode,mode)
            .eq(StringUtils.isNotBlank(status),ChatPrompt::getStatus,status).orderByDesc(ChatPrompt::getId)));
    }
    public ChatPrompt detail(Long id) {
        var p=mapper.selectById(id); if(p==null) throw new ServiceException("Prompt 模板不存在或无权访问"); return p;
    }
    public List<ChatPrompt> options(String mode) {
        return mapper.selectList(Wrappers.<ChatPrompt>lambdaQuery()
            .select(ChatPrompt::getId,ChatPrompt::getName,ChatPrompt::getDescription,ChatPrompt::getChatMode,ChatPrompt::getVersion)
            .eq(ChatPrompt::getStatus,"enabled").eq(ChatPrompt::getChatMode,ChatModePolicy.normalize(mode))
            .orderByAsc(ChatPrompt::getName,ChatPrompt::getId));
    }
    public ChatPrompt resolve(Long id,String mode) {
        if(id==null) return null;
        var p=detail(id);
        if(!"enabled".equals(p.getStatus()) || !ChatModePolicy.normalize(mode).equals(p.getChatMode()))
            throw new ServiceException("模板已停用或不适用于当前模式，请重新选择");
        return p;
    }
    public Long save(Long id,ChatPromptSave body) {
        if(!List.of("enabled","disabled").contains(body.status())) throw new ServiceException("无效的模板状态");
        String mode=ChatModePolicy.normalize(body.chatMode());
        if(id==null) {
            var p=new ChatPrompt();p.setName(body.name().trim());p.setDescription(body.description());p.setContent(body.content().trim());
            p.setChatMode(mode);p.setStatus(body.status());p.setVersion(1);mapper.insert(p);return p.getId();
        }
        detail(id);
        if(body.version()==null) throw new ServiceException("缺少模板版本，请刷新后重试");
        int count=mapper.update(null,Wrappers.<ChatPrompt>lambdaUpdate().eq(ChatPrompt::getId,id).eq(ChatPrompt::getVersion,body.version())
            .set(ChatPrompt::getName,body.name().trim()).set(ChatPrompt::getDescription,body.description())
            .set(ChatPrompt::getContent,body.content().trim()).set(ChatPrompt::getChatMode,mode)
            .set(ChatPrompt::getStatus,body.status()).set(ChatPrompt::getVersion,body.version()+1));
        if(count==0) throw new ServiceException("模板已更新，请刷新后重试");return id;
    }
    public void remove(Long id,Integer version) {
        if(version==null || mapper.delete(Wrappers.<ChatPrompt>lambdaQuery().eq(ChatPrompt::getId,id).eq(ChatPrompt::getVersion,version))==0)
            throw new ServiceException("模板已更新或删除，请刷新后重试");
    }
}
