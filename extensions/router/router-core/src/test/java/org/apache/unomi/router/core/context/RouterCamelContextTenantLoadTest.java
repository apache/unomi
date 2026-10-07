/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.router.core.context;

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * UNOMI-1000: router startup must load recurring configs for every tenant.
 */
public class RouterCamelContextTenantLoadTest {

    @Test
    @SuppressWarnings("unchecked")
    public void loadConfigurationsAcrossTenantsVisitsEveryTenant() throws Exception {
        Set<String> visitedTenants = new HashSet<>();
        AtomicReference<String> currentTenant = new AtomicReference<>();

        ImportConfiguration configA = new ImportConfiguration();
        configA.setItemId("import-a");
        ImportConfiguration configB = new ImportConfiguration();
        configB.setItemId("import-b");

        ImportExportConfigurationService<ImportConfiguration> service =
                new ImportExportConfigurationService<ImportConfiguration>() {
                    @Override
                    public List<ImportConfiguration> getAll() {
                        String tenant = currentTenant.get();
                        if ("tenant-a".equals(tenant)) {
                            return Collections.singletonList(configA);
                        }
                        if ("tenant-b".equals(tenant)) {
                            return Collections.singletonList(configB);
                        }
                        return Collections.emptyList();
                    }

                    @Override
                    public ImportConfiguration load(String configId) {
                        return null;
                    }

                    @Override
                    public ImportConfiguration save(ImportConfiguration configuration, boolean updateRunningRoute) {
                        return configuration;
                    }

                    @Override
                    public void delete(String configId) {
                    }

                    @Override
                    public Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh() {
                        return Collections.emptyMap();
                    }

                    @Override
                    public void requeueForRefresh(String tenantId, String configId,
                                                 RouterConstants.CONFIG_CAMEL_REFRESH refreshType) {
                    }
                };

        TenantService tenantService = (TenantService) Proxy.newProxyInstance(
                TenantService.class.getClassLoader(),
                new Class<?>[]{TenantService.class},
                (proxy, method, args) -> {
                    if ("getAllTenants".equals(method.getName())) {
                        return Arrays.asList(simpleTenant("tenant-a"), simpleTenant("tenant-b"));
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == long.class || method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });

        ExecutionContextManager contextManager = new ExecutionContextManager() {
            @Override
            public ExecutionContext getCurrentContext() {
                return null;
            }

            @Override
            public void setCurrentContext(ExecutionContext context) {
            }

            @Override
            public <T> T executeAsSystem(Supplier<T> operation) {
                return operation.get();
            }

            @Override
            public void executeAsSystem(Runnable operation) {
                operation.run();
            }

            @Override
            public <T> T executeAsTenant(String tenantId, Supplier<T> operation) {
                visitedTenants.add(tenantId);
                currentTenant.set(tenantId);
                try {
                    return operation.get();
                } finally {
                    currentTenant.set(null);
                }
            }

            @Override
            public void executeAsTenant(String tenantId, Runnable operation) {
                visitedTenants.add(tenantId);
                currentTenant.set(tenantId);
                try {
                    operation.run();
                } finally {
                    currentTenant.set(null);
                }
            }

            @Override
            public ExecutionContext createContext(String tenantId) {
                return null;
            }
        };

        RouterCamelContext context = new RouterCamelContext();
        context.setContextManager(contextManager);
        context.setTenantService(tenantService);

        Method method = RouterCamelContext.class.getDeclaredMethod(
                "loadConfigurationsAcrossTenants", ImportExportConfigurationService.class);
        method.setAccessible(true);
        List<ImportConfiguration> loaded = (List<ImportConfiguration>) method.invoke(context, service);

        assertEquals("expected one config per real tenant", 2, loaded.size());
        assertTrue(loaded.stream().anyMatch(c -> "import-a".equals(c.getItemId())));
        assertTrue(loaded.stream().anyMatch(c -> "import-b".equals(c.getItemId())));
        assertTrue(visitedTenants.contains("tenant-a"));
        assertTrue(visitedTenants.contains("tenant-b"));
        assertTrue(visitedTenants.contains(TenantService.SYSTEM_TENANT));
    }

    private static Tenant simpleTenant(String id) {
        Tenant tenant = new Tenant();
        tenant.setItemId(id);
        return tenant;
    }
}
