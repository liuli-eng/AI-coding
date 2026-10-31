package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.domain.vo.knowledge.KnowledgeSettingsVo;
import org.ruoyi.mapper.knowledge.KnowledgeSettingsMapper;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class KnowledgeSettingsPolicyTest {
    @Test void defaultsPreserveExistingDocumentAndCodeFormats() {
        var defaults=KnowledgeSettingsService.defaults();
        assertTrue(defaults.allowedTypes().containsAll(Set.of("pdf","docx","xlsx","txt","md","java","json")));
        assertFalse(defaults.overwriteSameName());
        assertEquals(0.5,defaults.similarityThreshold());
    }
    @Test void disabledFormatCannotReachObjectStorage() {
        var service=new KnowledgeSettingsService(mock(KnowledgeSettingsMapper.class));
        var policy=new KnowledgeSettingsVo("测试系统",Set.of("txt"),false,"请重新上传",0.5,0,null,null);
        var file=new MockMultipartFile("file","DATA.PDF","application/pdf",new byte[]{1});
        assertEquals(KnowledgeDocumentException.Reason.TYPE_DISABLED,assertThrows(KnowledgeDocumentException.class,()->service.validateUpload(file,policy)).reason());
        assertEquals("txt",service.validateUpload(new MockMultipartFile("file","a.TXT","text/plain",new byte[]{1}),policy));
    }
    @Test void invalidOrEmptyFilesAreRejectedBeforeReadingOrUploading() {
        var service=new KnowledgeSettingsService(mock(KnowledgeSettingsMapper.class));
        for(String name:new String[]{"", "a.exe", "a\n.txt"})assertThrows(KnowledgeDocumentException.class,()->service.validateUpload(new MockMultipartFile("file",name,"text/plain",new byte[]{1}),KnowledgeSettingsService.defaults()));
        assertThrows(KnowledgeDocumentException.class,()->service.validateUpload(new MockMultipartFile("file","a.txt","text/plain",new byte[0]),KnowledgeSettingsService.defaults()));
    }
}
