package org.ruoyi.service.chat;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.*;
import org.ruoyi.common.tenant.handle.PlusTenantLineHandler;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.common.tenant.properties.TenantProperties;
import org.ruoyi.mapper.chat.ChatQuestionMapper;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatQuestionTenantSqlTest {
    @Test void bothOverviewAggregatesUseTheCurrentTenant() throws Exception {
        var properties = new TenantProperties(); properties.setExcludes(List.of("trace_run", "trace_node"));
        var interceptor = new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties));
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("tenant-a");
            for (String method : List.of("totals", "models")) {
                String sql = ChatQuestionMapper.class.getMethod(method, Date.class, Date.class)
                    .getAnnotation(Select.class).value()[0].replace("#{begin}", "?").replace("#{end}", "?");
                String scoped = interceptor.parserSingle(sql, null);
                assertTrue(scoped.contains("tenant_id = 'tenant-a'"), scoped);
                assertTrue(scoped.contains("started_at >= ?"), scoped);
                assertTrue(scoped.contains("started_at < ?"), scoped);
            }
        }
    }
}
