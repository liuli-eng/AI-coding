package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.ruoyi.domain.bo.knowledge.KnowledgeAttachBo;
import org.ruoyi.domain.entity.knowledge.KnowledgeSettings;
import org.ruoyi.domain.vo.knowledge.KnowledgeAttachVo;
import org.ruoyi.domain.vo.knowledge.KnowledgeOptionVo;
import org.ruoyi.mapper.knowledge.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in integration test; the runner supplies a disposable DB and restricted user. */
@Tag("dev")
@EnabledIfEnvironmentVariable(named="DOCUMENTS_TEST_JDBC_URL",matches="jdbc:mysql:.*")
class KnowledgeDocumentMapperMySQLTest {
    @Test void realMySQLQueriesScopeTenantsPaginateAndKeepIndependentStatistics() throws Exception {
        var source=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",System.getenv("DOCUMENTS_TEST_JDBC_URL"),System.getenv("DOCUMENTS_TEST_USER"),System.getenv("DOCUMENTS_TEST_PASSWORD"));
        try(var connection=source.getConnection();var sql=connection.createStatement()){
            sql.executeUpdate("INSERT INTO knowledge_info(id,name,tenant_id,status) VALUES(900000000000001,'本租户资料',7,'0'),(900000000000002,'另一租户',8,'0')");
            sql.executeUpdate("INSERT INTO knowledge_attach(id,knowledge_id,tenant_id,name,type,doc_id,status,business_state,file_size,create_time) VALUES(900000000000011,900000000000001,7,'a.txt','txt','a',0,'0',10,NOW()),(900000000000012,900000000000001,7,'b.txt','txt','b',2,'0',20,NOW()),(900000000000013,900000000000001,7,'c.txt','txt','c',3,'1',30,NOW()),(900000000000014,900000000000002,8,'private.txt','txt','private',2,'0',40,NOW()),(900000000000015,900000000000002,7,'wrong-parent.txt','txt','wrong',2,'0',50,NOW())");
        }
        var configuration=new MybatisConfiguration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("scratch",new JdbcTransactionFactory(),source));
        var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));configuration.addInterceptor(plugin);
        configuration.addMapper(KnowledgeAttachMapper.class);configuration.addMapper(KnowledgeSettingsMapper.class);
        try(var session=new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)){
            var mapper=session.getMapper(KnowledgeAttachMapper.class);var query=new KnowledgeAttachBo();
            var page=mapper.workbench(new Page<KnowledgeAttachVo>(1,2),"000007",query);
            assertEquals(3,page.getTotal());assertEquals(2,page.getRecords().size());assertEquals("c.txt",page.getRecords().get(0).getName());assertEquals(30L,page.getRecords().get(0).getFileSize());assertEquals("本租户资料",page.getRecords().get(0).getKnowledgeName());
            var second=mapper.workbench(new Page<KnowledgeAttachVo>(2,2),"000007",query);assertEquals(1,second.getRecords().size());assertEquals("a.txt",second.getRecords().get(0).getName());
            query.setStatus(3);assertEquals(1,mapper.workbench(new Page<KnowledgeAttachVo>(1,20),"000007",query).getTotal());
            var stats=mapper.statistics("000007",query);assertEquals(3,stats.getTotal());assertEquals(1,stats.getWaiting());assertEquals(1,stats.getCompleted());assertEquals(1,stats.getFailed());
            query.setBusinessState("0");assertEquals(2,mapper.statistics("000007",query).getTotal());
            query.setBusinessState(null);query.setStatus(null);query.setName("%' OR 1=1 --");assertEquals(0,mapper.workbench(new Page<KnowledgeAttachVo>(1,20),"000007",query).getTotal());
            var options=mapper.knowledgeOptions(new Page<KnowledgeOptionVo>(1,20),"000007",null);assertEquals(1,options.getTotal());assertEquals("本租户资料",options.getRecords().get(0).getName());
            var settings=session.getMapper(KnowledgeSettingsMapper.class);var row=new KnowledgeSettings();row.setTenantId("000007");row.setSystemName("本租户系统");row.setAllowedTypes("[\"txt\"]");row.setFailureMessage("解析失败");row.setSimilarityThreshold(.7);row.setOverwriteSameName(true);row.setUpdateBy(7L);row.setUpdateTime(new java.util.Date());
            assertEquals(1,settings.insert(row));assertEquals(1,settings.get("000007").getVersion());assertNull(settings.get("000008"));
            row.setVersion(1);assertEquals(1,settings.update(row));assertEquals(0,settings.update(row));assertEquals(2,settings.get("000007").getVersion());
            var file=java.nio.file.Files.createTempFile("knowledge-parser-", ".txt");
            java.nio.file.Files.writeString(file,"document content");
            try{
                var oss=org.mockito.Mockito.mock(org.ruoyi.common.core.service.OssService.class);
                var object=new org.ruoyi.common.core.domain.dto.OssDTO();object.setOssId(99L);object.setUrl(file.toUri().toString());
                org.mockito.Mockito.when(oss.selectByIds("99")).thenReturn(java.util.List.of(object));
                var libraries=org.mockito.Mockito.mock(IKnowledgeInfoService.class);var info=new org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo();info.setEmbeddingModel("test");
                org.mockito.Mockito.when(libraries.queryById(900000000000001L)).thenReturn(info);
                var models=org.mockito.Mockito.mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
                org.mockito.Mockito.when(models.selectModelByName("test")).thenReturn(new org.ruoyi.common.chat.domain.vo.chat.ChatModelVo());
                var factory=org.mockito.Mockito.mock(org.ruoyi.factory.ResourceLoaderFactory.class);var loader=org.mockito.Mockito.mock(ResourceLoader.class);
                org.mockito.Mockito.when(factory.getLoaderByFileType("txt")).thenReturn(loader);
                org.mockito.Mockito.when(loader.getChunkList(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of("fragment"));
                var policy=org.mockito.Mockito.mock(KnowledgeSettingsService.class);org.mockito.Mockito.when(policy.forTenant(org.mockito.ArgumentMatchers.anyString())).thenReturn(KnowledgeSettingsService.defaults());
                var service=new org.ruoyi.service.knowledge.impl.KnowledgeAttachServiceImpl(mapper,libraries,org.mockito.Mockito.mock(KnowledgeFragmentMapper.class),models,factory,org.mockito.Mockito.mock(org.ruoyi.service.vector.VectorStoreService.class),oss,org.mockito.Mockito.mock(org.ruoyi.service.retrieval.KnowledgeRetrievalService.class),policy,org.mockito.Mockito.mock(KnowledgeAdminAccess.class),org.mockito.Mockito.mock(KnowledgeParseFailureRecorder.class),new KnowledgeDocumentStorage(oss));
                for(boolean fail:new boolean[]{false,true}){
                    mapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<org.ruoyi.domain.entity.knowledge.KnowledgeAttach>().eq("id",900000000000011L).set("oss_id",99L).set("status",0).set("business_state","0"));
                    org.mockito.Mockito.when(loader.getContent(org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
                        mapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<org.ruoyi.domain.entity.knowledge.KnowledgeAttach>().eq("id",900000000000011L).set("business_state","1"));
                        if(fail)throw new IllegalStateException("private-parser-error");return "content";
                    });
                    service.parse(900000000000011L);
                    var parsed=mapper.selectById(900000000000011L);
                    assertEquals(fail?3:2,parsed.getStatus());assertEquals("1",parsed.getBusinessState(),"A parser must never undo disable actions made during parsing");
                    if(fail)assertEquals(KnowledgeSettingsService.defaults().failureMessage(),parsed.getRemark());
                }
            }finally{java.nio.file.Files.delete(file);}

            // Exercise the real transaction boundary used by uploads. Compensation itself opens
            // a new transaction, matching SysOssServiceImpl.deleteFile rather than a plain mock.
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
            var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
            var outer=new org.springframework.transaction.support.TransactionTemplate(manager);
            var independent=new org.springframework.transaction.support.TransactionTemplate(manager);
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var objectPresent=new java.util.concurrent.atomic.AtomicBoolean();
            var storageOss=org.mockito.Mockito.mock(org.ruoyi.common.core.service.OssService.class);
            org.mockito.Mockito.when(storageOss.uploadFile(org.mockito.ArgumentMatchers.any(org.springframework.web.multipart.MultipartFile.class))).thenAnswer(call->{
                jdbc.update("INSERT INTO sys_oss(oss_id,tenant_id,file_name,original_name,file_suffix,url,service) VALUES(900000000000099,'000007','fixture-key','fixture.txt','.txt','http://fixture/file','test')");
                objectPresent.set(true);var dto=new org.ruoyi.common.core.domain.dto.OssDTO();dto.setOssId(900000000000099L);return dto;
            });
            org.mockito.Mockito.when(storageOss.deleteFile(900000000000099L)).thenAnswer(call->independent.execute(transaction->{
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=900000000000099",Integer.class),"Compensation must see the independently committed metadata");
                jdbc.update("DELETE FROM sys_oss WHERE oss_id=900000000000099");objectPresent.set(false);return true;
            }));
            var proxy=new org.springframework.aop.framework.ProxyFactory(new KnowledgeDocumentStorage(storageOss));
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var storage=(KnowledgeDocumentStorage)proxy.getProxy();
            outer.execute(transaction->{
                var uploaded=storage.upload(new org.springframework.mock.web.MockMultipartFile("file","fixture.txt","text/plain",new byte[]{1}));
                storageOss.deleteFile(uploaded.getOssId());transaction.setRollbackOnly();return null;
            });
            assertFalse(objectPresent.get());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=900000000000099",Integer.class));

        }
    }
}
