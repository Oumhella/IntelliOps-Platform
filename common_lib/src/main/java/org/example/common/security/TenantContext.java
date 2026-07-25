package org.example.common.security;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    public static void setEnterpriseId(Long enterpriseId) {
        CURRENT_TENANT.set(enterpriseId);
    }

    public static Long getEnterpriseId() {
        return CURRENT_TENANT.get();
    }

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
    }
}
