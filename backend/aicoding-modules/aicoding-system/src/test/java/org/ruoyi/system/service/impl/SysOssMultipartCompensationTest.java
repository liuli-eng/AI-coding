package org.ruoyi.system.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.ConfigService;
import org.ruoyi.common.oss.core.OssClient;
import org.ruoyi.common.oss.entity.UploadResult;
import org.ruoyi.common.oss.factory.OssFactory;
import org.ruoyi.system.domain.SysOss;
import org.ruoyi.system.mapper.SysOssMapper;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class SysOssMultipartCompensationTest {
    @BeforeAll static void context(){var context=new GenericApplicationContext();context.registerBean(ObjectMapper.class,()->new ObjectMapper());context.refresh();new SpringUtil().setApplicationContext(context);}
    private final SysOssMapper mapper=mock(SysOssMapper.class);
    private final OssClient storage=mock(OssClient.class);
    private final SysOssServiceImpl service=new SysOssServiceImpl(mapper,mock(ConfigService.class));
    private final MockMultipartFile file=new MockMultipartFile("file","material.txt","text/plain",new byte[]{1,2,3});
    private void uploaded(){when(storage.getConfigKey()).thenReturn("test-store");when(storage.uploadSuffix(any(byte[].class),eq(".txt"),anyString())).thenReturn(UploadResult.builder().filename("objects/generated-key.txt").url("https://invalid.test/generated-key.txt").build());}
    @Test void metadataInsertFailureDeletesObjectBeforeOssDtoExists(){
        uploaded();var failure=new IllegalStateException("metadata insert failed");when(mapper.insert(any(SysOss.class))).thenThrow(failure);
        try(var factory=mockStatic(OssFactory.class)){factory.when(OssFactory::instance).thenReturn(storage);assertSame(failure,assertThrows(IllegalStateException.class,()->service.uploadFile(file)));verify(storage).delete("objects/generated-key.txt");}
    }
    @Test void zeroMetadataRowsAreFailureAndDeleteObject(){
        uploaded();when(mapper.insert(any(SysOss.class))).thenReturn(0);
        try(var factory=mockStatic(OssFactory.class)){factory.when(OssFactory::instance).thenReturn(storage);assertThrows(ServiceException.class,()->service.uploadFile(file));verify(storage).delete("objects/generated-key.txt");}
    }
    @Test void cleanupFailureIsSuppressedAndOriginalMetadataFailureSurvives(){
        uploaded();var failure=new IllegalStateException("metadata insert failed");var cleanup=new IllegalStateException("object delete failed");when(mapper.insert(any(SysOss.class))).thenThrow(failure);doThrow(cleanup).when(storage).delete("objects/generated-key.txt");
        try(var factory=mockStatic(OssFactory.class)){factory.when(OssFactory::instance).thenReturn(storage);assertSame(failure,assertThrows(IllegalStateException.class,()->service.uploadFile(file)));assertArrayEquals(new Throwable[]{cleanup},failure.getSuppressed());}
    }
}
