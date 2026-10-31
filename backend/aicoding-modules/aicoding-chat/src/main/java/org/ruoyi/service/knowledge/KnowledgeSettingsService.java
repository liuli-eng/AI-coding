package org.ruoyi.service.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.json.utils.JsonUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.entity.knowledge.KnowledgeSettings;
import org.ruoyi.domain.vo.knowledge.KnowledgeSettingsVo;
import org.ruoyi.mapper.knowledge.KnowledgeSettingsMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static org.ruoyi.service.knowledge.KnowledgeDocumentException.Reason.*;

@Service @RequiredArgsConstructor
public class KnowledgeSettingsService {
    public static final int MAX_FILE_SIZE=20*1024*1024;
    private static final Set<String> SUPPORTED=Set.of("txt","md","pdf","doc","docx","xlsx","xls","csv","json","java","html","htm","css","js","ts","py","cpp","c","h","hpp","sql","php","ruby","swift","rs","perl","shell","bat","cmd","xml","yaml","yml","properties","ini","log");
    private final KnowledgeSettingsMapper mapper;
    public static KnowledgeSettingsVo defaults(){return new KnowledgeSettingsVo("AI 编程助手",SUPPORTED,false,"文档解析失败，请检查文件内容后重试。",0.5,0,null,null);}
    public static String tenant(){
        if(!LoginHelper.isLogin()||LoginHelper.getUserId()==null)throw new KnowledgeDocumentException(UNAVAILABLE);
        String tenant=TenantHelper.getTenantId();
        if(StringUtils.isBlank(tenant))tenant=LoginHelper.getTenantId();
        if(StringUtils.isBlank(tenant))throw new KnowledgeDocumentException(UNAVAILABLE);
        return tenant;
    }
    public KnowledgeSettingsVo current(){return forTenant(tenant());}
    public KnowledgeSettingsVo forTenant(String tenant){
        if(StringUtils.isBlank(tenant))throw new KnowledgeDocumentException(UNAVAILABLE);
        var row=mapper.get(tenant);
        if(row==null)return defaults();
        return new KnowledgeSettingsVo(row.getSystemName(),Set.copyOf(JsonUtils.parseArray(row.getAllowedTypes(),String.class)),Boolean.TRUE.equals(row.getOverwriteSameName()),row.getFailureMessage(),row.getSimilarityThreshold(),row.getVersion(),row.getUpdateTime(),row.getUpdatedBy());
    }
    public KnowledgeSettingsVo save(KnowledgeSettingsVo input){
        String tenant=tenant();
        validate(input);
        var row=new KnowledgeSettings();row.setTenantId(tenant);row.setSystemName(input.systemName().trim());
        row.setAllowedTypes(JsonUtils.toJsonString(new TreeSet<>(input.allowedTypes())));
        row.setOverwriteSameName(input.overwriteSameName());row.setFailureMessage(input.failureMessage().trim());row.setSimilarityThreshold(input.similarityThreshold());
        row.setVersion(input.version());row.setUpdateBy(LoginHelper.getUserId());row.setUpdateTime(new Date());
        try {
            if(input.version()==0){if(mapper.insert(row)!=1)throw new KnowledgeDocumentException(SETTINGS_CONFLICT);}
            else if(mapper.update(row)!=1)throw new KnowledgeDocumentException(SETTINGS_CONFLICT);
        }catch(DuplicateKeyException conflict){throw new KnowledgeDocumentException(SETTINGS_CONFLICT);}
        return current();
    }
    public KnowledgeSettingsVo reset(int version){
        var d=defaults();return save(new KnowledgeSettingsVo(d.systemName(),d.allowedTypes(),d.overwriteSameName(),d.failureMessage(),d.similarityThreshold(),version,null,null));
    }
    private void validate(KnowledgeSettingsVo s){
        if(s==null||s.version()<0||s.systemName()==null||s.systemName().isBlank()||s.systemName().length()>100||s.systemName().chars().anyMatch(Character::isISOControl)
            ||s.allowedTypes()==null||s.allowedTypes().isEmpty()||!SUPPORTED.containsAll(s.allowedTypes())
            ||s.failureMessage()==null||s.failureMessage().isBlank()||s.failureMessage().length()>255
            ||s.similarityThreshold()==null||!Double.isFinite(s.similarityThreshold())||s.similarityThreshold()<0||s.similarityThreshold()>1)
            throw new KnowledgeDocumentException(INVALID_SETTINGS);
    }
    public String validateUpload(MultipartFile file,KnowledgeSettingsVo settings){
        if(file==null||file.isEmpty())throw new KnowledgeDocumentException(EMPTY_FILE);
        if(file.getSize()>MAX_FILE_SIZE)throw new KnowledgeDocumentException(FILE_TOO_LARGE);
        String name=filename(file);
        int dot=name.lastIndexOf('.');String suffix=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!settings.allowedTypes().contains(suffix))throw new KnowledgeDocumentException(TYPE_DISABLED);
        return suffix;
    }
    public static String filename(MultipartFile file){
        String name=Objects.toString(file.getOriginalFilename(),"").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        if(name.isBlank()||name.length()>255||name.chars().anyMatch(Character::isISOControl))throw new KnowledgeDocumentException(INVALID_FILE);
        return name;
    }
}
