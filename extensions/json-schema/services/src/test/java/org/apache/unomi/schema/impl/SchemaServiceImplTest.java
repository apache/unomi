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
package org.apache.unomi.schema.impl;

import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.listener.JsonSchemaListener;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaServiceImplTest {

    private SchemaServiceImpl schemaService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private TestBundleContext bundleContext;
    private JsonSchemaListener schemaListener;
    private KarafSecurityService securityService;
    private ExecutionContextManager contextManager;

    private static final String TENANT_1 = "tenant1";
    private static final String SYSTEM_TENANT = "system";

    @Before
    public void setUp() {
        tenantService = new TestTenantService();
        securityService = TestHelper.createSecurityService();
        contextManager = TestHelper.createExecutionContextManager(securityService);

        // Create tenants
        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(SYSTEM_TENANT, Collections.singletonMap("description", "System tenant"));
            tenantService.createTenant(TENANT_1, Collections.singletonMap("description", "Tenant 1"));
            return null;
        });

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up persistence service
        persistenceService = new InMemoryPersistenceServiceImpl(contextManager, conditionEvaluatorDispatcher);

        // Set up bundle context with predefined data
        bundleContext = new TestBundleContext();
        Bundle systemBundle = mock(Bundle.class);
        when(systemBundle.getBundleId()).thenReturn(0L);
        when(systemBundle.getSymbolicName()).thenReturn("org.apache.unomi.predefined");
        when(systemBundle.getBundleContext()).thenReturn(bundleContext);

        bundleContext.addBundle(systemBundle);

        // Create predefined schemas
        URL schemasUrl = getClass().getResource("/META-INF/cxs/schemas/predefined-schemas.json");
        when(bundleContext.getBundle().findEntries("META-INF/cxs/schemas", "*.json", true))
                .thenReturn(Collections.enumeration(Arrays.asList(schemasUrl)));

        // Set up schema service
        schemaService = new SchemaServiceImpl();
        schemaService.setPersistenceService(persistenceService);
        schemaService.setTenantService(tenantService);
        schemaService.setContextManager(contextManager);
        schemaService.init();

        // Set up schema listener
        schemaListener = new JsonSchemaListener();
        schemaListener.setBundleContext(bundleContext);
        schemaListener.setSchemaService(schemaService);
        schemaListener.postConstruct();
    }

    @Test
    public void testPredefinedSchemas() {
        // Test
        schemaService.refreshJSONSchemas();
        JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/events/test");

        // Verify predefined schema exists
        assertNotNull(result);
        assertEquals(SYSTEM_TENANT, result.getTenantId());
        assertEquals("test", result.getName());
    }

    @Test
    public void testSchemaInheritance_CurrentTenant() {
        // Setup
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper schema = createTestSchema("test-schema", "test");
            persistenceService.save(schema);

            // Test
            schemaService.refreshJSONSchemas();
            JsonSchemaWrapper result = schemaService.getSchema("test-schema");

            // Verify
            assertNotNull(result);
            assertEquals(TENANT_1, result.getTenantId());
            return null;
        });
    }

    @Test
    public void testSchemaInheritance_SystemTenant() {
        // Setup
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper systemSchema = createTestSchema("test-schema", "test");
            persistenceService.save(systemSchema);
            return null;
        });

        // Switch to tenant1 and test
        contextManager.executeAsTenant(TENANT_1, () -> {
            schemaService.refreshJSONSchemas();

            // Test
            JsonSchemaWrapper result = schemaService.getSchema("test-schema");

            // Verify
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            return null;
        });
    }

    @Test
    public void testSchemaInheritance_TenantOverride() {
        // Setup system schema
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper systemSchema = createTestSchema("test-schema", "system-version");
            persistenceService.save(systemSchema);
            return null;
        });

        // Setup tenant schema and test
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper tenantSchema = createTestSchema("test-schema", "tenant-version");
            persistenceService.save(tenantSchema);

            // Test
            schemaService.refreshJSONSchemas();
            JsonSchemaWrapper result = schemaService.getSchema("test-schema");

            // Verify
            assertNotNull(result);
            assertEquals(TENANT_1, result.getTenantId());
            assertEquals("tenant-version", result.getName());
            return null;
        });
    }

    @Test
    public void testSchemaInheritance_MergeResults() {
        // Setup system schemas
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper systemOnlySchema = createTestSchema("system-only", "system-only");
            persistenceService.save(systemOnlySchema);

            JsonSchemaWrapper systemOverrideSchema = createTestSchema("shared-schema", "system-version");
            persistenceService.save(systemOverrideSchema);
            return null;
        });

        // Setup tenant schemas and test
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper tenantOnlySchema = createTestSchema("tenant-only", "tenant-only");
            persistenceService.save(tenantOnlySchema);

            JsonSchemaWrapper tenantOverrideSchema = createTestSchema("shared-schema", "tenant-version");
            persistenceService.save(tenantOverrideSchema);

            // Test
            schemaService.refreshJSONSchemas();
            Set<String> allSchemaIds = schemaService.getInstalledJsonSchemaIds();

            // Verify
            assertNotNull(allSchemaIds);
            Map<String, JsonSchemaWrapper> schemaMap = new HashMap<>();
            for (String schemaId : allSchemaIds) {
                JsonSchemaWrapper schema = schemaService.getSchema(schemaId);
                schemaMap.put(schema.getItemId(), schema);
            }

            // Verify system-only schema
            JsonSchemaWrapper systemOnly = schemaMap.get("system-only");
            assertNotNull(systemOnly);
            assertEquals(SYSTEM_TENANT, systemOnly.getTenantId());
            assertEquals("system-only", systemOnly.getName());

            // Verify tenant-only schema
            JsonSchemaWrapper tenantOnly = schemaMap.get("tenant-only");
            assertNotNull(tenantOnly);
            assertEquals(TENANT_1, tenantOnly.getTenantId());
            assertEquals("tenant-only", tenantOnly.getName());

            // Verify overridden schema
            JsonSchemaWrapper overridden = schemaMap.get("shared-schema");
            assertNotNull(overridden);
            assertEquals(TENANT_1, overridden.getTenantId());
            assertEquals("tenant-version", overridden.getName());
            return null;
        });
    }

    @Test
    public void testSchemaValidation_SystemTenant() {
        // Setup system schema
        contextManager.executeAsSystem(() -> {
            String validationSchema = "{ \"$id\": \"test-validation\", \"type\": \"object\", \"required\": [\"name\"], \"properties\": { \"name\": { \"type\": \"string\" } } }";
            JsonSchemaWrapper schema = new JsonSchemaWrapper("test-validation", validationSchema, "test", "validation", null, new Date());
            schema.setTenantId(SYSTEM_TENANT);
            persistenceService.save(schema);
            return null;
        });

        // Test from tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            schemaService.refreshJSONSchemas();
            JsonSchemaWrapper result = schemaService.getSchema("test-validation");

            // Verify schema validation works
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertTrue(result.getSchema().contains("\"required\": [\"name\"]"));
            return null;
        });
    }

    @Test
    public void testSchemaByTarget_SystemTenant() {
        // Setup system schema with target
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper systemSchema = createTestSchemaWithScope("scoped-schema", "system", "test-scope");
            persistenceService.save(systemSchema);
            return null;
        });

        // Test from tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            schemaService.refreshJSONSchemas();
            List<JsonSchemaWrapper> scopedSchemas = schemaService.getSchemasByTarget("test-scope");

            // Verify
            assertNotNull(scopedSchemas);
            assertFalse(scopedSchemas.isEmpty());
            JsonSchemaWrapper result = scopedSchemas.get(0);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertEquals("system", result.getName());
            return null;
        });
    }

    private JsonSchemaWrapper createTestSchema(String id, String name) {
        String schema = String.format("{ \"$id\": \"%s\", \"self\": { \"name\": \"%s\" } }", id, name);
        JsonSchemaWrapper wrapper = new JsonSchemaWrapper(id, schema, "test", name, null, new Date());
        wrapper.setTenantId(contextManager.getCurrentContext().getTenantId());
        return wrapper;
    }

    private JsonSchemaWrapper createTestSchemaWithScope(String id, String name, String target) {
        String schema = String.format("{ \"$id\": \"%s\", \"self\": { \"name\": \"%s\", \"target\": \"%s\" } }", id, name, target);
        JsonSchemaWrapper wrapper = new JsonSchemaWrapper(id, schema, target, name, null, new Date());
        wrapper.setTenantId(contextManager.getCurrentContext().getTenantId());
        return wrapper;
    }
}
