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
import org.apache.unomi.schema.api.ValidationException;
import org.apache.unomi.schema.api.ValidationError;
import org.apache.unomi.schema.listener.JsonSchemaListener;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.apache.commons.io.IOUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URL;
import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;

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
    private TracerService tracerService;

    private static final String TENANT_1 = "tenant1";
    private static final String SYSTEM_TENANT = "system";

    private interface RuntimeSupplier<T> {
        T get();
    }

    @Before
    public void setUp() {
        tenantService = new TestTenantService();
        securityService = TestHelper.createSecurityService();
        securityService.setCurrentSubject(securityService.createSubject(TENANT_1, true));
        contextManager = TestHelper.createExecutionContextManager(securityService);
        tracerService = TestHelper.createTracerService();

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
        schemaService.setTracerService(tracerService);
        schemaService.init();

        // Set up schema listener
        schemaListener = new JsonSchemaListener();
        schemaListener.setBundleContext(bundleContext);
        schemaListener.setSchemaService(schemaService);
        schemaListener.postConstruct();
    }

    @Test
    public void testPredefinedSchemas() {
        // The schema listener in setup should have already loaded the predefined schemas
        schemaService.refreshJSONSchemas();

        // Test from system context
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/events/test");

            // Verify predefined schema exists
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertEquals("test", result.getName());
            assertEquals("events", result.getTarget());

            // Also verify schema can be found through target lookup
            List<JsonSchemaWrapper> eventSchemas = schemaService.getSchemasByTarget("events");
            assertFalse("Should have at least one event schema", eventSchemas.isEmpty());
            boolean foundTestSchema = eventSchemas.stream()
                    .anyMatch(schema -> "test".equals(schema.getName()) &&
                              "https://unomi.apache.org/schemas/json/events/test".equals(schema.getItemId()));
            assertTrue("Predefined test schema should be found in events target", foundTestSchema);
            return null;
        });

        // Test access from another tenant
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/events/test");

            // Non-system tenant should still have access to system schemas
            assertNotNull("Tenant should see predefined schemas", result);
            assertEquals("Predefined schema should be from system tenant", SYSTEM_TENANT, result.getTenantId());
            assertEquals("test", result.getName());
            return null;
        });
    }

    @Test
    public void testSchemaInheritance_CurrentTenant() {
        // Setup - Create a schema in tenant1
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant-specific-schema.json", TENANT_1);
                persistenceService.save(schema);

                // Test - Get from same tenant
                schemaService.refreshJSONSchemas();
                JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/tenant-specific-schema");

                // Verify - Should get tenant schema
                assertNotNull("Should find schema in current tenant", result);
                assertEquals("Schema should be from tenant1", TENANT_1, result.getTenantId());
                assertEquals("tenant-test", result.getName());

                // Create another tenant to test isolation
                contextManager.executeAsSystem(() -> {
                    tenantService.createTenant("tenant-test-isolation", Collections.singletonMap("description", "Isolation Test Tenant"));
                    return null;
                });

                // Switch to the other tenant and verify tenant isolation
                contextManager.executeAsTenant("tenant-test-isolation", () -> {
                    // This should NOT find the tenant1 schema - schemas aren't shared between tenants
                    JsonSchemaWrapper isolatedResult = schemaService.getSchema("https://unomi.apache.org/schemas/json/tenant-specific-schema");
                    assertNull("Other tenant should not see tenant1 schema", isolatedResult);
                    return null;
                });

                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testSchemaInheritance_SystemTenant() throws IOException {
        // Setup
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-inheritance-schema.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Switch to tenant1 and test
        contextManager.executeAsTenant(TENANT_1, () -> {
            schemaService.refreshJSONSchemas();

            // Test
            JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/test/system-inheritance-schema");

            // Verify
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertEquals("test", result.getName());
            return null;
        });
    }

    @Test
    public void testSchemaInheritance_TenantOverride() {
        // Setup system schema
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-override-schema.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Setup tenant schema and test
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenantSchema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant-override-schema.json", TENANT_1);
                persistenceService.save(tenantSchema);

                // Test
                schemaService.refreshJSONSchemas();
                JsonSchemaWrapper result = schemaService.getSchema("https://unomi.apache.org/schemas/json/test/override-schema");

                // Verify
                assertNotNull(result);
                assertEquals(TENANT_1, result.getTenantId());
                assertEquals("tenant-version", result.getName());
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testSchemaInheritance_MergeResults() throws IOException {
        // Setup system schemas
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemOnlySchema = loadSchemaFromResource("/META-INF/cxs/schemas/merge-system-only.json", SYSTEM_TENANT);
                persistenceService.save(systemOnlySchema);

                JsonSchemaWrapper systemOverrideSchema = loadSchemaFromResource("/META-INF/cxs/schemas/merge-shared-schema-system.json", SYSTEM_TENANT);
                persistenceService.save(systemOverrideSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load system schemas
        schemaService.refreshJSONSchemas();

        // Add tenant schemas in a separate step
        try {
            // Execute in tenant context
            contextManager.executeAsTenant(TENANT_1, () -> {
                try {
                    JsonSchemaWrapper tenantOnlySchema = loadSchemaFromResource("/META-INF/cxs/schemas/merge-tenant-only.json", TENANT_1);
                    persistenceService.save(tenantOnlySchema);

                    JsonSchemaWrapper tenantOverrideSchema = loadSchemaFromResource("/META-INF/cxs/schemas/merge-shared-schema-tenant.json", TENANT_1);
                    persistenceService.save(tenantOverrideSchema);
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            // Refresh schemas again to load tenant schemas
            schemaService.refreshJSONSchemas();

            // Test - still in tenant context
            contextManager.executeAsTenant(TENANT_1, () -> {
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
        } catch (Exception e) {
            throw new RuntimeException("Error testing schema inheritance", e);
        }
    }

    @Test
    public void testSchemaValidation_SystemTenant() throws IOException {
        // Setup system schema
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/validation-schema.json", SYSTEM_TENANT);
                persistenceService.save(schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
    public void testSchemaByTarget_SystemTenant() throws IOException {
        // Setup system schema with target
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/test-scope-schema.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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

    @Test
    public void testTenantSpecificSchema_Validation() throws IOException {
        String schemaId = "https://unomi.apache.org/schemas/json/test/dual-tenant-schema-tenant1";
        String systemSchemaId = "https://unomi.apache.org/schemas/json/test/dual-tenant-schema-system";

        // Tenant 1 schema - requires a "name" field
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenant1Schema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant1-schema.json", TENANT_1);
                persistenceService.save(tenant1Schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // System tenant schema - requires an "id" field (different validation rules)
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-schema.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load both
        schemaService.refreshJSONSchemas();

        // Test validation with Tenant 1
        contextManager.executeAsTenant(TENANT_1, () -> {
            // Data with name (valid for tenant1) but no id (invalid for system)
            String validForTenant1 = "{ \"name\": \"testName\" }";
            // Data with id (valid for system) but no name (invalid for tenant1)
            String validForSystem = "{ \"id\": \"testId\" }";

            // Should validate against tenant1's schema (requiring name)
            assertTrue(schemaService.isValid(validForTenant1, schemaId));
            assertFalse(schemaService.isValid(validForSystem, schemaId));
            return null;
        });

        // Test validation with System tenant
        contextManager.executeAsSystem(() -> {
            // Data with name (valid for tenant1) but no id (invalid for system)
            String validForTenant1 = "{ \"name\": \"testName\" }";
            // Data with id (valid for system) but no name (invalid for tenant1)
            String validForSystem = "{ \"id\": \"testId\" }";

            // Should validate against system's schema (requiring id)
            assertFalse(schemaService.isValid(validForTenant1, systemSchemaId));
            assertTrue(schemaService.isValid(validForSystem, systemSchemaId));
            return null;
        });
    }

    @Test
    public void testTenantSpecificSchema_CrossTenantValidation() throws IOException {
        String eventName = "test-event";

        // System tenant schema - requires "systemField"
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-event-schema.json", SYSTEM_TENANT);
                systemSchema.setTenantId(SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Tenant 1 schema - requires "tenantField"
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenantSchema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant-event-schema.json", TENANT_1);
                tenantSchema.setTenantId(TENANT_1);
                persistenceService.save(tenantSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // Test validation of events in each tenant
        contextManager.executeAsTenant(TENANT_1, () -> {
            // Event valid for tenant 1, invalid for system
            String event1 = "{ \"eventType\": \"" + eventName + "\", \"tenantField\": \"value\" }";
            // Event valid for system, invalid for tenant 1
            String event2 = "{ \"eventType\": \"" + eventName + "\", \"systemField\": \"value\" }";

            // Should validate against tenant1's schema
            assertTrue(schemaService.isEventValid(event1));
            assertFalse(schemaService.isEventValid(event2));
            return null;
        });

        contextManager.executeAsSystem(() -> {
            // Event valid for tenant 1, invalid for system
            String event1 = "{ \"eventType\": \"" + eventName + "\", \"tenantField\": \"value\" }";
            // Event valid for system, invalid for tenant 1
            String event2 = "{ \"eventType\": \"" + eventName + "\", \"systemField\": \"value\" }";

            // Should validate against system's schema
            assertFalse(schemaService.isEventValid(event1));
            assertTrue(schemaService.isEventValid(event2));
            return null;
        });
    }

    @Test
    public void testDynamicSchemaUpdates() throws IOException {
        String schemaId = "https://unomi.apache.org/schemas/json/test/dynamic-schema";

        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper initialSchema = loadSchemaFromResource("/META-INF/cxs/schemas/initial-schema.json", TENANT_1);
                initialSchema.setTenantId(TENANT_1);
                persistenceService.save(initialSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // Test initial validation
        contextManager.executeAsTenant(TENANT_1, () -> {
            String validInitial = "{ \"initialField\": \"value\" }";
            String invalidInitial = "{ \"updatedField\": \"value\" }";

            assertTrue(schemaService.isValid(validInitial, schemaId));
            assertFalse(schemaService.isValid(invalidInitial, schemaId));
            return null;
        });

        // Update schema in tenant 1
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper updatedSchema = loadSchemaFromResource("/META-INF/cxs/schemas/updated-schema.json", TENANT_1);
                updatedSchema.setTenantId(TENANT_1);
                persistenceService.save(updatedSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to pick up changes
        schemaService.refreshJSONSchemas();

        // Test validation with updated schema
        contextManager.executeAsTenant(TENANT_1, () -> {
            String validInitial = "{ \"initialField\": \"value\" }";
            String validUpdated = "{ \"updatedField\": \"value\" }";

            // Now the requirements have changed
            assertFalse(schemaService.isValid(validInitial, schemaId));
            assertTrue(schemaService.isValid(validUpdated, schemaId));
            return null;
        });
    }

    @Test
    public void testGetSchemaForEventType_TenantIsolation() throws IOException, ValidationException {
        String eventName = "test-event";

        // Create two event schemas with the same name but different validation rules in different tenants

        // System tenant schema
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-event-schema.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Tenant 1 schema
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenantSchema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant-event-schema.json", TENANT_1);
                persistenceService.save(tenantSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load both
        schemaService.refreshJSONSchemas();

        // 1. Test from Tenant 1 context - should get tenant-specific schema
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper schema = schemaService.getSchemaForEventType(eventName);
                assertNotNull(schema);
                assertEquals(TENANT_1, schema.getTenantId());
                assertEquals("https://unomi.apache.org/schemas/json/events/tenant-event-schema", schema.getItemId());
                return null;
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        });

        // 2. Test from System tenant context - should get system schema
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper schema = schemaService.getSchemaForEventType(eventName);
                assertNotNull(schema);
                assertEquals(SYSTEM_TENANT, schema.getTenantId());
                assertEquals("https://unomi.apache.org/schemas/json/events/system-event-schema", schema.getItemId());
                return null;
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        });

        // 3. Create another tenant without this schema and verify it falls back to system tenant
        final String TENANT_2 = "tenant2";
        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));
            return null;
        });

        contextManager.executeAsTenant(TENANT_2, () -> {
            try {
                JsonSchemaWrapper schema = schemaService.getSchemaForEventType(eventName);
                assertNotNull(schema);
                assertEquals(SYSTEM_TENANT, schema.getTenantId());
                assertEquals("https://unomi.apache.org/schemas/json/events/system-event-schema", schema.getItemId());
                return null;
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        });

        // 4. Test with non-existent event type - should throw exception
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                schemaService.getSchemaForEventType("non-existent-event");
                fail("Should have thrown ValidationException for non-existent event type");
                return null;
            } catch (ValidationException e) {
                // Expected behavior
                assertTrue(e.getMessage().contains("Schema not found for event type"));
                return null;
            }
        });
    }

    @Test
    public void testGetSchemasByTarget_TenantIsolation() throws IOException {
        String targetName = "test-target";

        // System tenant schemas with the target
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema1 = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema1.json", SYSTEM_TENANT);
                JsonSchemaWrapper systemSchema2 = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema2.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema1);
                persistenceService.save(systemSchema2);

                // Create a schema with different target (should not be returned)
                JsonSchemaWrapper differentTargetSchema = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema5.json", SYSTEM_TENANT);
                persistenceService.save(differentTargetSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Tenant 1 schemas with the target
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenantSchema1 = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema3.json", TENANT_1);
                JsonSchemaWrapper tenantSchema2 = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema4.json", TENANT_1);
                persistenceService.save(tenantSchema1);
                persistenceService.save(tenantSchema2);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Create another tenant with schemas with the same target
        final String TENANT_2 = "tenant2";
        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));
            return null;
        });

        contextManager.executeAsTenant(TENANT_2, () -> {
            try {
                JsonSchemaWrapper tenant2Schema = loadSchemaFromResource("/META-INF/cxs/schemas/target-schema6.json", TENANT_2);
                persistenceService.save(tenant2Schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // 1. Test from Tenant 1 context - should get tenant1 schemas + system schemas
        contextManager.executeAsTenant(TENANT_1, () -> {
            List<JsonSchemaWrapper> schemas = schemaService.getSchemasByTarget(targetName);

            // Should have 4 schemas: 2 from tenant1 + 2 from system
            assertEquals(4, schemas.size());

            // Count schemas by tenant
            long tenant1Count = schemas.stream()
                    .filter(schema -> TENANT_1.equals(schema.getTenantId()))
                    .count();
            long systemCount = schemas.stream()
                    .filter(schema -> SYSTEM_TENANT.equals(schema.getTenantId()))
                    .count();
            long tenant2Count = schemas.stream()
                    .filter(schema -> TENANT_2.equals(schema.getTenantId()))
                    .count();

            assertEquals(2, tenant1Count);
            assertEquals(2, systemCount);
            assertEquals(0, tenant2Count); // Should not have any schemas from tenant2
            return null;
        });

        // 2. Test from System tenant context - should get only system schemas
        contextManager.executeAsSystem(() -> {
            List<JsonSchemaWrapper> schemas = schemaService.getSchemasByTarget(targetName);

            // Should have only 2 schemas from system tenant
            assertEquals(2, schemas.size());

            // All schemas should be from system tenant
            for (JsonSchemaWrapper schema : schemas) {
                assertEquals(SYSTEM_TENANT, schema.getTenantId());
            }
            return null;
        });

        // 3. Test from Tenant 2 context - should get tenant2 schemas + system schemas
        contextManager.executeAsTenant(TENANT_2, () -> {
            List<JsonSchemaWrapper> schemas = schemaService.getSchemasByTarget(targetName);

            // Should have 3 schemas: 1 from tenant2 + 2 from system
            assertEquals(3, schemas.size());

            // Count schemas by tenant
            long tenant1Count = schemas.stream()
                    .filter(schema -> TENANT_1.equals(schema.getTenantId()))
                    .count();
            long systemCount = schemas.stream()
                    .filter(schema -> SYSTEM_TENANT.equals(schema.getTenantId()))
                    .count();
            long tenant2Count = schemas.stream()
                    .filter(schema -> TENANT_2.equals(schema.getTenantId()))
                    .count();

            assertEquals(0, tenant1Count); // Should not have any schemas from tenant1
            assertEquals(2, systemCount);
            assertEquals(1, tenant2Count);
            return null;
        });

        // 4. Test with non-existent target - should return empty list
        contextManager.executeAsTenant(TENANT_1, () -> {
            List<JsonSchemaWrapper> schemas = schemaService.getSchemasByTarget("non-existent-target");
            assertNotNull(schemas);
            assertTrue(schemas.isEmpty());
            return null;
        });
    }

    @Test
    public void testSchemaExtension_MergingMechanism() throws IOException, ValidationException {
        // Create a base view event schema in system tenant (similar to the doc example)
        String baseSchemaId = "https://unomi.apache.org/schemas/json/events/view/properties/1-0-0";
        String extension1Id = "https://vendor.test.com/schemas/json/events/dummy/extension/1-0-0";
        String extension2Id = "https://apache.org/schemas/json/events/system/extension/1-0-0";
        
        // 1. Create base view event schema in system tenant
        contextManager.executeAsSystem(() -> {
            try {
                // Load base schema
                JsonSchemaWrapper baseSchema1 = loadSchemaFromResource("/META-INF/cxs/schemas/view-event-schema.json", SYSTEM_TENANT);
                baseSchema1.setTenantId(SYSTEM_TENANT);
                persistenceService.save(baseSchema1);
                
                // Load system tenant extension
                JsonSchemaWrapper systemExtSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-extension.json", SYSTEM_TENANT);
                systemExtSchema.setTenantId(SYSTEM_TENANT);
                persistenceService.save(systemExtSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        
        // 2. Create tenant extension
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                // Load tenant extension schema
                JsonSchemaWrapper tenant1ExtSchema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant1-extension.json", TENANT_1);
                tenant1ExtSchema.setTenantId(TENANT_1);
                persistenceService.save(tenant1ExtSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        
        // Refresh schemas to load all schemas
        schemaService.refreshJSONSchemas();
        
        // 3. Test the dynamic merging functionality during validation
        
        // From Tenant 1 Context - should apply both tenant and system extensions
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                // Test data validation with extensions
                
                // Data with only base schema properties - should fail (missing required properties from extensions)
                String baseOnlyData = "{ \"source\": \"web\", \"url\": \"https://example.org\" }";
                
                // Data with all required properties - should pass validation
                String fullData = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"myNewProp\": \"value\", \"systemProp\": \"value\" }";
                
                // Validate directly using the schema - this should trigger dynamic merging
                boolean baseOnlyValid = schemaService.isValid(baseOnlyData, baseSchemaId);
                boolean fullDataValid = schemaService.isValid(fullData, baseSchemaId);
                
                assertFalse("Data with only base properties should fail validation", baseOnlyValid);
                assertTrue("Data with all extension properties should pass validation", fullDataValid);
                
                // Validate directly against the extensions to ensure they're applied separately
                boolean missingTenantProp = schemaService.isValid(
                    "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\" }", 
                    baseSchemaId);
                boolean missingSystemProp = schemaService.isValid(
                    "{ \"source\": \"web\", \"url\": \"https://example.org\", \"myNewProp\": \"value\" }", 
                    baseSchemaId);
                    
                assertFalse("Should fail without tenant extension property", missingTenantProp);
                assertFalse("Should fail without system extension property", missingSystemProp);
                
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // From System Context - should only apply system extension
        contextManager.executeAsSystem(() -> {
            try {
                // Data with only base schema properties - should fail (missing system extension property)
                String baseOnlyData = "{ \"source\": \"web\", \"url\": \"https://example.org\" }";
                
                // Data with base + system extension properties - should pass
                String withSystemData = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\" }";
                
                // Data with tenant extension property - should be ignored in system context
                String withTenantData = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\", \"myNewProp\": \"value\" }";
                
                // Validate directly using the schema - this should trigger dynamic merging
                boolean baseOnlyValid = schemaService.isValid(baseOnlyData, baseSchemaId);
                boolean withSystemValid = schemaService.isValid(withSystemData, baseSchemaId);
                boolean withTenantValid = schemaService.isValid(withTenantData, baseSchemaId);
                
                assertFalse("Data with only base properties should fail validation", baseOnlyValid);
                assertTrue("Data with system properties should pass validation", withSystemValid);
                assertTrue("Data with tenant property should pass validation in system context", withTenantValid);
                
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // 4. Test from a fresh tenant that doesn't have its own extensions
        final String TENANT_2 = "tenant2";
        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));
            return null;
        });
        
        // New tenant should inherit system extensions but not tenant1 extensions
        contextManager.executeAsTenant(TENANT_2, () -> {
            try {
                // Data with only base schema properties - should fail (missing system extension property)
                String baseOnlyData = "{ \"source\": \"web\", \"url\": \"https://example.org\" }";
                
                // Data with base + system extension properties - should pass
                String withSystemData = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\" }";
                
                // Data with tenant1 extension property - should be ignored (not tenant2's extension)
                String withTenantData = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\", \"myNewProp\": \"value\" }";
                
                // Validate directly using the schema - this should trigger dynamic merging
                boolean baseOnlyValid = schemaService.isValid(baseOnlyData, baseSchemaId);
                boolean withSystemValid = schemaService.isValid(withSystemData, baseSchemaId);
                boolean withTenantValid = schemaService.isValid(withTenantData, baseSchemaId);
                
                assertFalse("Data with only base properties should fail validation", baseOnlyValid);
                assertTrue("Data with system properties should pass validation", withSystemValid);
                assertTrue("Data with other tenant's property should pass validation (ignored)", withTenantValid);
                
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // 5. Verify we can add a tenant-specific extension to tenant2 and it will be applied
        contextManager.executeAsTenant(TENANT_2, () -> {
            try {
                // Load tenant2 extension schema from file
                JsonSchemaWrapper tenant2ExtSchema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant2-extension.json", TENANT_2);
                persistenceService.save(tenant2ExtSchema);
                
                // Refresh schemas
                schemaService.refreshJSONSchemas();
                
                // Now tenant2 should require tenant2Prop in addition to systemProp
                
                // Data with just system property - should now fail (missing tenant2 property)
                String withSystemOnly = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\" }";
                
                // Data with system + tenant2 properties - should pass
                String withTenant2Prop = "{ \"source\": \"web\", \"url\": \"https://example.org\", \"systemProp\": \"value\", \"tenant2Prop\": \"value\" }";
                
                boolean systemOnlyValid = schemaService.isValid(withSystemOnly, baseSchemaId);
                boolean withTenant2Valid = schemaService.isValid(withTenant2Prop, baseSchemaId);
                
                assertFalse("Should now fail without tenant2 extension property", systemOnlyValid);
                assertTrue("Should pass with tenant2 extension property", withTenant2Valid);
                
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testGetSchema_TenantIsolation() {
        // Create schemas with the same ID in different tenants to test proper isolation
        String schemaId = "https://unomi.apache.org/schemas/json/test/shared-id-schema";

        // Create system tenant schema
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/schema1.json", SYSTEM_TENANT);
                persistenceService.save(systemSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Create tenant1 schema with same ID
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper tenantSchema = loadSchemaFromResource("/META-INF/cxs/schemas/schema2.json", TENANT_1);
                persistenceService.save(tenantSchema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Create tenant2 without this schema
        final String TENANT_2 = "tenant2-getschema-test";
        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));
            return null;
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // 1. Test from System context - should get system schema
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper result = schemaService.getSchema(schemaId);
            assertNotNull("System should find the schema", result);
            assertEquals("System should get its own schema", SYSTEM_TENANT, result.getTenantId());
            assertEquals("system-version", result.getName());
            return null;
        });

        // 2. Test from Tenant1 context - should get tenant schema (overriding system)
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper result = schemaService.getSchema(schemaId);
            assertNotNull("Tenant1 should find the schema", result);
            assertEquals("Tenant1 should get its own schema", TENANT_1, result.getTenantId());
            assertEquals("tenant1-version", result.getName());
            return null;
        });

        // 3. Test from Tenant2 context - should get system schema (fallback)
        contextManager.executeAsTenant(TENANT_2, () -> {
            JsonSchemaWrapper result = schemaService.getSchema(schemaId);
            assertNotNull("Tenant2 should find the schema", result);
            assertEquals("Tenant2 should get system schema", SYSTEM_TENANT, result.getTenantId());
            assertEquals("system-version", result.getName());
            return null;
        });

        // 4. Test non-existent schema
        String nonExistentId = "https://unomi.apache.org/schemas/json/non-existent";
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper result = schemaService.getSchema(nonExistentId);
            assertNull("Should not find non-existent schema", result);
            return null;
        });
    }

    @Test
    public void testDeleteSchema() throws IOException {
        // Create a schema in the system tenant
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant1-schema.json", SYSTEM_TENANT);
                schema.setTenantId(TENANT_1);
                persistenceService.save(schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load the new schema
        schemaService.refreshJSONSchemas();

        // 1. Verify schema exists before deletion
        contextManager.executeAsTenant(TENANT_1, () -> {
            String schemaId = "https://unomi.apache.org/schemas/json/test/dual-tenant-schema-tenant1";
            JsonSchemaWrapper schema = schemaService.getSchema(schemaId);
            assertNotNull("Schema should exist before deletion", schema);
            assertEquals("Schema ID should match", schemaId, schema.getItemId());
            assertEquals("Schema should be from tenant1 tenant", TENANT_1, schema.getTenantId());

            // 2. Delete the schema
            boolean deleted = schemaService.deleteSchema(schemaId);
            assertTrue("Schema deletion should succeed", deleted);

            // 3. Verify schema no longer exists after deletion
            JsonSchemaWrapper deletedSchema = schemaService.getSchema(schemaId);
            assertNull("Schema should not exist after deletion", deletedSchema);

            // 4. Try to delete a non-existent schema
            boolean nonExistentDeleted = schemaService.deleteSchema("non-existent-schema");
            assertFalse("Deleting non-existent schema should return false", nonExistentDeleted);

            return null;
        });

        // 5. Test tenant isolation for delete operation
        String tenantSchemaId = "https://unomi.apache.org/schemas/json/tenant-specific-schema";

        // Create a tenant-specific schema
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/tenant-specific-schema.json", TENANT_1);
                persistenceService.save(schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // Try to delete tenant schema from system context - should fail
        contextManager.executeAsSystem(() -> {
            JsonSchemaWrapper tenantSchema = schemaService.getSchema(tenantSchemaId);
            assertNull("System tenant should not see tenant-specific schema", tenantSchema);

            boolean deleted = schemaService.deleteSchema(tenantSchemaId);
            assertFalse("System tenant should not be able to delete tenant schema", deleted);
            return null;
        });

        // Verify tenant schema still exists from tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            JsonSchemaWrapper tenantSchema = schemaService.getSchema(tenantSchemaId);
            assertNotNull("Tenant schema should still exist", tenantSchema);
            assertEquals("Tenant schema should be from tenant1", TENANT_1, tenantSchema.getTenantId());
            return null;
        });
    }

    @Test
    public void testIsEventValid_AutoDetect() throws IOException {
        // Setup - create an event schema in the system tenant
        contextManager.executeAsSystem(() -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/autodetect-system-event-schema.json", SYSTEM_TENANT);
                schema.setTenantId(SYSTEM_TENANT);
                persistenceService.save(schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load the new schema
        schemaService.refreshJSONSchemas();

        // Test the isEventValid method that auto-detects event type
        contextManager.executeAsSystem(() -> {
            // Valid event with eventType field
            String validEvent = "{ \"eventType\": \"system\", \"scope\": \"test\", \"properties\": { \"systemProperty\": \"value\" } }";
            boolean isValid = schemaService.isEventValid(validEvent);
            assertTrue("Valid event should pass validation", isValid);

            // Invalid event missing required property
            String invalidEvent = "{ \"eventType\": \"system\", \"scope\": \"test\", \"properties\": {} }";
            boolean isInvalid = schemaService.isEventValid(invalidEvent);
            assertFalse("Event missing required property should fail validation", isInvalid);

            // Event with unknown eventType
            String unknownTypeEvent = "{ \"eventType\": \"unknown\", \"scope\": \"test\" }";
            boolean isUnknownValid = schemaService.isEventValid(unknownTypeEvent);
            assertFalse("Event with unknown type should fail validation", isUnknownValid);

            // Malformed JSON
            String malformedEvent = "{ \"eventType\": \"system\", \"scope\": \"test\", \"properties\": { \"missing\": ";
            boolean isMalformedValid = schemaService.isEventValid(malformedEvent);
            assertFalse("Malformed JSON should fail validation", isMalformedValid);

            // Missing eventType field
            String missingTypeEvent = "{ \"scope\": \"test\", \"properties\": { \"systemProperty\": \"value\" } }";
            boolean isMissingTypeValid = schemaService.isEventValid(missingTypeEvent);
            assertFalse("Event missing eventType field should fail validation", isMissingTypeValid);

            return null;
        });

        // Test tenant isolation - create tenant-specific event schema
        contextManager.executeAsTenant(TENANT_1, () -> {
            try {
                JsonSchemaWrapper schema = loadSchemaFromResource("/META-INF/cxs/schemas/autodetect-tenant-event-schema.json", TENANT_1);
                persistenceService.save(schema);
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // Test from tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            // Valid event for tenant schema
            String validTenantEvent = "{ \"eventType\": \"tenant\", \"scope\": \"test\", \"properties\": { \"tenantProperty\": \"value\" } }";
            boolean isValid = schemaService.isEventValid(validTenantEvent);
            assertTrue("Valid tenant event should pass validation", isValid);

            // System event should also be visible from tenant context
            String validSystemEvent = "{ \"eventType\": \"system\", \"scope\": \"test\", \"properties\": { \"systemProperty\": \"value\" } }";
            boolean isSystemValid = schemaService.isEventValid(validSystemEvent);
            assertTrue("System event should pass validation from tenant context", isSystemValid);

            return null;
        });
    }

    @Test
    public void testValidateEvents_BatchValidation() throws IOException, ValidationException {
        // Setup - create event schemas in the system tenant
        contextManager.executeAsSystem(() -> {
            try {
                // System event schema
                JsonSchemaWrapper systemSchema = loadSchemaFromResource("/META-INF/cxs/schemas/system-event-schema.json", SYSTEM_TENANT);
                systemSchema.setTenantId(SYSTEM_TENANT);
                persistenceService.save(systemSchema);

                // Predefined test schema (from predefined-schemas.json)
                // This is loaded during setup from the mocked bundle.findEntries

                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Refresh schemas to load the new schemas
        schemaService.refreshJSONSchemas();

        // Test batch validation with multiple events
        contextManager.executeAsSystem(() -> {
            try {
                // Prepare batch of events with different validation outcomes
                String events = "["
                    + "{ \"eventType\": \"test-event\", \"systemField\": \"test\" },"                                            // valid system event
                    + "{ \"eventType\": \"test-event\" },"                                                                       // invalid system event (missing required property)
                    + "{ \"eventType\": \"test\", \"scope\": \"scope\", \"properties\": {} },"                                   // valid test event
                    + "{ \"eventType\": \"unknown\", \"scope\": \"test\" },"                                                     // unknown event type
                    + "{ \"scope\": \"test\", \"properties\": { \"systemProperty\": \"value\" } }"                               // missing eventType
                    + "]";

                // Validate batch of events
                Map<String, Set<ValidationError>> validationResults = schemaService.validateEvents(events);

                // Assertions
                assertNotNull("Validation results should not be null", validationResults);

                // Check which event types have validation errors
                // The map should contain errors for 'system' (invalid event) and 'unknown' (unknown type)
                // And possibly 'error' for the event missing eventType field

                // System events - should have errors because one event is invalid
                assertTrue("Should have errors for system event type", validationResults.containsKey("test-event"));
                assertFalse("System event type should have validation errors", validationResults.get("test-event").isEmpty());

                // Test events - should not have errors (valid test event)
                assertFalse("Should not have errors for test event type", validationResults.containsKey("test"));

                // Unknown event type - should have errors
                assertTrue("Should have errors for unknown event type",
                    validationResults.containsKey("unknown") || validationResults.containsKey("error"));

                // Missing eventType event - should produce an error (may be under 'error' key)
                assertTrue("Should have generic error for missing eventType",
                    validationResults.containsKey("error"));

                return null;
            } catch (ValidationException e) {
                throw new RuntimeException(e);
            }
        });

        // Test malformed JSON array
        contextManager.executeAsSystem(() -> {
            try {
                // Not a valid JSON array
                String invalidJson = "{ \"not\": \"an array\" }";

                Map<String, Set<ValidationError>> validationResults = schemaService.validateEvents(invalidJson);

                // Should return a generic error with key "error"
                assertNotNull("Validation results should not be null", validationResults);
                assertTrue("Should have generic error key", validationResults.containsKey("error"));
                assertFalse("Error set should not be empty", validationResults.get("error").isEmpty());

                return null;
            } catch (ValidationException e) {
                // This is expected for implementation that throws exceptions
                // If the implementation returns error map instead, this catch block won't execute
                assertNotNull("Exception should have a message", e.getMessage());
                return null;
            }
        });

        // Test with empty array
        contextManager.executeAsSystem(() -> {
            try {
                String emptyArray = "[]";

                Map<String, Set<ValidationError>> validationResults = schemaService.validateEvents(emptyArray);

                // Should return empty map
                assertNotNull("Validation results should not be null", validationResults);
                assertTrue("Should have no validation results for empty array", validationResults.isEmpty());

                return null;
            } catch (ValidationException e) {
                fail("Should not throw exception for empty array: " + e.getMessage());
                return null;
            }
        });
    }

    @Test
    public void testUnloadPredefinedSchema() throws IOException {
        // Setup - create a new instance of the schema service with known predefined schemas
        URL predefinedSchemaUrl = getClass().getResource("/META-INF/cxs/schemas/predefined-schemas.json");
        assertNotNull("Predefined schema file should exist", predefinedSchemaUrl);

        // 1. Load the predefined schema
        try (InputStream predefinedSchemaStream = predefinedSchemaUrl.openStream()) {
            // Load into the schema service
            schemaService.loadPredefinedSchema(predefinedSchemaStream);
        }

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // Verify predefined schema is loaded
        contextManager.executeAsSystem(() -> {
            String testEventSchemaId = "https://unomi.apache.org/schemas/json/events/test";
            JsonSchemaWrapper testSchema = schemaService.getSchema(testEventSchemaId);
            assertNotNull("Predefined schema should be loaded", testSchema);
            assertEquals("Schema should have correct ID", testEventSchemaId, testSchema.getItemId());
            return null;
        });

        // 2. Now unload the predefined schema
        try (InputStream predefinedSchemaStream = predefinedSchemaUrl.openStream()) {
            boolean unloaded = schemaService.unloadPredefinedSchema(predefinedSchemaStream);
            assertTrue("Schema should be successfully unloaded", unloaded);
        }

        // Refresh schemas
        schemaService.refreshJSONSchemas();

        // 3. Verify the schema is no longer available
        contextManager.executeAsSystem(() -> {
            String testEventSchemaId = "https://unomi.apache.org/schemas/json/events/test";
            JsonSchemaWrapper testSchema = schemaService.getSchema(testEventSchemaId);
            assertNull("Predefined schema should be unloaded", testSchema);
            return null;
        });

        // 4. Test unloading a non-existent schema
        try (InputStream nonExistentSchemaStream =
                new ByteArrayInputStream(
                        "{ \"$id\": \"https://unomi.apache.org/schemas/json/events/non-existant\", \"$schema\": \"https://json-schema.org/draft/2019-09/schema\", \"self\": { \"name\": \"non-existent\", \"target\": \"test\" }, \"type\": \"object\" }".getBytes(StandardCharsets.UTF_8))) {
            boolean unloaded = schemaService.unloadPredefinedSchema(nonExistentSchemaStream);
            assertFalse("Unloading non-existent schema should return false", unloaded);
        }

        // 5. Test unloading an invalid schema
        try (InputStream invalidSchemaStream = new ByteArrayInputStream("{ not valid json".getBytes(StandardCharsets.UTF_8))) {
            boolean unloaded = schemaService.unloadPredefinedSchema(invalidSchemaStream);
            assertFalse("Unloading invalid schema should return false", unloaded);
        }
    }

    private JsonSchemaWrapper loadSchemaFromResource(String resourcePath, String tenantId) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            String schemaContent = IOUtils.toString(is, StandardCharsets.UTF_8);

            // Parse schema manually
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schemaNode = mapper.readTree(schemaContent);

            String schemaId = schemaNode.get("$id").asText();
            String target = schemaNode.at("/self/target").asText();
            String name = schemaNode.at("/self/name").asText();
            String extendsSchemaId = null;
            JsonNode extendsNode = schemaNode.at("/self/extends");
            if (!extendsNode.isMissingNode() && !extendsNode.isNull()) {
                extendsSchemaId = extendsNode.asText();
            }

            JsonSchemaWrapper wrapper = new JsonSchemaWrapper(schemaId, schemaContent, target, name, extendsSchemaId, new Date());
            wrapper.setTenantId(tenantId);
            return wrapper;
        }
    }
}
