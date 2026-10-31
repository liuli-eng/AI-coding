package org.ruoyi.service.learning;

import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.utils.StringUtils;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.tenant.helper.TenantHelper;

/** Explicit authenticated scope, also when the optional tenant plugin is disabled. */
final class CourseAccess {
    private CourseAccess() {}
    static Long userId() {
        Long user=LoginHelper.getUserId();
        if(user==null||user<=0)throw new ServiceException("请先登录");
        return user;
    }
    // The legacy session column is BIGINT: JDBC returns "0" for authenticated tenant "000000".
    static boolean sameSessionTenant(String authenticated,String stored) {
        if(authenticated==null||stored==null)return false;
        try{return new java.math.BigInteger(authenticated).equals(new java.math.BigInteger(stored));}
        catch(NumberFormatException e){return authenticated.equals(stored);}
    }
    static String tenantId() {
        userId();
        String tenant=TenantHelper.getTenantId();
        if(StringUtils.isBlank(tenant))tenant=LoginHelper.getTenantId();
        if(StringUtils.isBlank(tenant))throw new ServiceException("请先登录");
        return tenant;
    }
}
