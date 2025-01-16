package org.apache.unomi.services.impl;

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Custom TenantService implementation for testing
public class TestTenantService implements TenantService {
    private ThreadLocal<String> currentTenantId = new ThreadLocal<>();

    public static final String SYSTEM_TENANT = "system";

    public void setCurrentTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    @Override
    public String getCurrentTenantId() {
        return currentTenantId.get();
    }

    @Override
    public void setCurrentTenant(String tenantId) {
        setCurrentTenantId(tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return Collections.emptyList();
    }

    @Override
    public Tenant getTenant(String tenantId) {
        return null;
    }

    @Override
    public void saveTenant(Tenant tenant) {
        // No-op for test
    }

    @Override
    public void deleteTenant(String tenantId) {
        // No-op for test
    }

    @Override
    public boolean validateApiKey(String tenantId, String apiKey) {
        return true;
    }

    @Override
    public Tenant createTenant(String tenantId, Map<String, Object> properties) {
        return null;
    }

    @Override
    public ApiKey generateApiKey(String tenantId, Long validityPeriod) {
        return null;
    }
}
