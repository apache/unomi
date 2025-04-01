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
package org.apache.unomi.groovy.actions.services.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.groovy.actions.GroovyActionDispatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GroovyActionsServiceImpl class.
 */
public class GroovyActionsServiceImplTest {

    private GroovyActionsServiceImpl groovyActionsService;
    private TenantService tenantService;
    private PersistenceService persistenceService;
    private BundleContext bundleContext;
    private ExecutionContextManager contextManager;
    private SchedulerService schedulerService;
    private MultiTypeCacheService cacheService;
    private KarafSecurityService securityService;
    private TracerService tracerService;
    private DefinitionsService definitionsService;
    private ExecutionContext executionContext;

    private static final String TENANT_1 = "tenant1";
    private static final String SYSTEM_TENANT = "system";

    @Before
    public void setUp() throws Exception {
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
        bundleContext = mock(BundleContext.class);
        Bundle systemBundle = mock(Bundle.class);
        when(systemBundle.getBundleId()).thenReturn(0L);
        when(systemBundle.getSymbolicName()).thenReturn("org.apache.unomi.predefined");
        when(systemBundle.getBundleContext()).thenReturn(bundleContext);
        BundleWiring bundleWiring = mock(BundleWiring.class);
        when(bundleWiring.getClassLoader()).thenReturn(getClass().getClassLoader());
        when(systemBundle.adapt(BundleWiring.class)).thenReturn(bundleWiring);
        when(bundleContext.getBundle()).thenReturn(systemBundle);
        when(bundleContext.getBundles()).thenReturn(new Bundle[] { systemBundle });
        when(bundleContext.getBundle().getHeaders()).thenReturn(new Hashtable<>());
        URL baseScriptURL = getClass().getResource("/META-INF/base/BaseScript.groovy");
        when(bundleContext.getBundle().getEntry("META-INF/base/BaseScript.groovy")).thenReturn(baseScriptURL);

        // Create predefined schemas
        URL schemasUrl = getClass().getResource("/META-INF/cxs/schemas/predefined-schemas.json");
        when(bundleContext.getBundle().findEntries("META-INF/cxs/schemas", "*.json", true))
                .thenReturn(Collections.enumeration(Arrays.asList(schemasUrl)));

        schedulerService = TestHelper.createSchedulerService("groovy-actions-service-scheduler-node", persistenceService, contextManager, bundleContext, null, -1, true, true);

        cacheService = new MultiTypeCacheServiceImpl();

        ConditionValidationService conditionValidationService = TestHelper.createConditionValidationService();
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, cacheService, contextManager, tenantService, conditionValidationService);

        // Set up Groovy actions service with spy to mock internal methods
        groovyActionsService = new GroovyActionsServiceImpl();

        // Set dependencies
        groovyActionsService.setPersistenceService(persistenceService);
        groovyActionsService.setTenantService(tenantService);
        groovyActionsService.setContextManager(contextManager);
        groovyActionsService.setSchedulerService(schedulerService);
        groovyActionsService.setCacheService(cacheService);
        groovyActionsService.setDefinitionsService(definitionsService);
        groovyActionsService.setBundleContext(bundleContext);

        // Create a mock config for the activate method
        GroovyActionsServiceImpl.GroovyActionsServiceConfig config = mock(GroovyActionsServiceImpl.GroovyActionsServiceConfig.class);
        when(config.services_groovy_actions_refresh_interval()).thenReturn(1000);

        groovyActionsService.activate(config, bundleContext);
    }

    @Test
    public void testSaveGroovyAction() {
        // Prepare test data
        String actionName = "testSaveAction";

        // Load the Groovy script from the resource file
        String groovyScript;
        try {
            URL resourceUrl = getClass().getResource("/META-INF/cxs/actions/testSaveAction.groovy");
            if (resourceUrl == null) {
                fail("Could not find test Groovy action file");
            }
            groovyScript = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(resourceUrl.toURI())));
        } catch (Exception e) {
            fail("Failed to load Groovy action from resource file: " + e.getMessage());
            return;
        }

        // Execute save action in tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            groovyActionsService.save(actionName, groovyScript);

            assertNotNull(definitionsService.getActionType(actionName));
        });
    }

    @Test
    public void testRemoveGroovyAction() {
        // First save an action
        String actionName = "testRemoveAction";

        // Load the Groovy script from the resource file
        String groovyScript;
        try {
            URL resourceUrl = getClass().getResource("/META-INF/cxs/actions/testRemoveAction.groovy");
            if (resourceUrl == null) {
                fail("Could not find test Groovy action file for removal test");
            }
            groovyScript = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(resourceUrl.toURI())));
        } catch (Exception e) {
            fail("Failed to load Groovy action from resource file: " + e.getMessage());
            return;
        }

        // Execute save and then remove action in tenant context
        contextManager.executeAsTenant(TENANT_1, () -> {
            // First save the action
            groovyActionsService.save(actionName, groovyScript);
            assertNotNull("Action should be saved before removal test", definitionsService.getActionType(actionName));

            // Then remove it
            groovyActionsService.remove(actionName);
            assertNull("Action should be removed after removal test", definitionsService.getActionType(actionName));
        });
    }

    @Test
    public void testExecuteGroovyAction() {
        // Prepare test data
        String actionName = "testExecuteAction";

        // Load the Groovy script from the resource file
        String groovyScript;
        try {
            URL resourceUrl = getClass().getResource("/META-INF/cxs/actions/testExecuteAction.groovy");
            if (resourceUrl == null) {
                fail("Could not find test Groovy action file for execution test");
            }
            groovyScript = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(resourceUrl.toURI())));
        } catch (Exception e) {
            fail("Failed to load Groovy action from resource file: " + e.getMessage());
            return;
        }

        // First save the Groovy action
        contextManager.executeAsTenant(TENANT_1, () -> {
            groovyActionsService.save(actionName, groovyScript);

            // Verify action type is saved properly
            ActionType actionType = definitionsService.getActionType(actionName);
            assertNotNull("Action type should be registered", actionType);
            assertEquals("Action should have 2 parameters", 2, actionType.getParameters().size());

            // Create a GroovyActionDispatcher to execute the action directly
            GroovyActionDispatcher groovyDispatcher = new GroovyActionDispatcher();
            groovyDispatcher.setTracerService(tracerService);
            groovyDispatcher.setGroovyActionsService(groovyActionsService);

            // Create an event
            Event event = new Event();
            event.setEventType("test");
            event.setScope("testScope");

            try {
                // Test 1: SESSION_UPDATED return value
                org.apache.unomi.api.actions.Action action1 = new org.apache.unomi.api.actions.Action();
                action1.setActionTypeId(actionName);
                action1.setParameter("returnType", "SESSION_UPDATED");
                action1.setParameter("shouldFail", false);

                int result1 = groovyDispatcher.execute(action1, event, actionName);
                assertEquals("Action should return SESSION_UPDATED", EventService.SESSION_UPDATED, result1);

                // Test 2: NO_CHANGE return value
                org.apache.unomi.api.actions.Action action2 = new org.apache.unomi.api.actions.Action();
                action2.setActionTypeId(actionName);
                action2.setParameter("returnType", "NO_CHANGE");
                action2.setParameter("shouldFail", false);

                int result2 = groovyDispatcher.execute(action2, event, actionName);
                assertEquals("Action should return NO_CHANGE", EventService.NO_CHANGE, result2);

                // Test 3: ERROR return value
                org.apache.unomi.api.actions.Action action3 = new org.apache.unomi.api.actions.Action();
                action3.setActionTypeId(actionName);
                action3.setParameter("returnType", "SESSION_UPDATED");
                action3.setParameter("shouldFail", true);

                int result3 = groovyDispatcher.execute(action3, event, actionName);
                assertEquals("Action should return ERROR", EventService.ERROR, result3);
            } catch (Exception e) {
                fail("Failed to execute Groovy action: " + e.getMessage());
            }
        });
    }

    @Test
    public void testGroovyShell() {
        // Verify that the Groovy shell is properly initialized
        groovy.lang.GroovyShell shell = groovyActionsService.getGroovyShell();
        assertNotNull("Groovy shell should be initialized", shell);

        // Don't check specific variables as they might be implementation details
        // Instead, test the functionality

        // Test evaluating a simple script
        try {
            Object result = shell.evaluate("2 + 2");
            assertEquals("Simple script should evaluate correctly", 4, result);
        } catch (Exception e) {
            fail("Failed to evaluate simple script: " + e.getMessage());
        }

        // Test importing classes that should be available through the ImportCustomizer
        try {
            Object result = shell.evaluate(
                "import org.apache.unomi.api.services.EventService\n" +
                "return EventService.NO_CHANGE");
            assertEquals("Should be able to import and use EventService", EventService.NO_CHANGE, result);
        } catch (Exception e) {
            fail("Failed to test imports: " + e.getMessage());
        }
    }

    @Test
    public void testLoadPredefinedGroovyActions() {
        // We'll use the existing service instance instead of creating a new one
        // Set up the bundle to find our test Groovy actions
        URL action1Url = getClass().getResource("/META-INF/cxs/actions/testSaveAction.groovy");
        URL action2Url = getClass().getResource("/META-INF/cxs/actions/testExecuteAction.groovy");

        // Mock the bundle context to return our test actions
        when(bundleContext.getBundle().findEntries("META-INF/cxs/actions", "*.groovy", true))
            .thenReturn(Collections.enumeration(Arrays.asList(action1Url, action2Url)));

        // Reset the service to force loading of predefined items
        groovyActionsService.preDestroy();

        // Re-activate the service with the existing bundle context
        GroovyActionsServiceImpl.GroovyActionsServiceConfig config = mock(GroovyActionsServiceImpl.GroovyActionsServiceConfig.class);
        when(config.services_groovy_actions_refresh_interval()).thenReturn(1000);
        groovyActionsService.activate(config, bundleContext);

        // Verify that the actions were loaded in the system tenant
        contextManager.executeAsSystem(() -> {
            // Test action types were registered properly
            ActionType saveActionType = definitionsService.getActionType("testSaveAction");
            assertNotNull("testSaveAction should be registered", saveActionType);

            ActionType executeActionType = definitionsService.getActionType("testExecuteAction");
            assertNotNull("testExecuteAction should be registered", executeActionType);
            return null;
        });

        // Verify we can get the GroovyCodeSource for the loaded actions
        contextManager.executeAsSystem(() -> {
            assertNotNull("Should have GroovyCodeSource for testSaveAction in system tenant",
                        groovyActionsService.getGroovyCodeSource("testSaveAction"));
            return null;
        });
    }

    @Test
    public void testDetailedActionAnnotation() {
        // Create a Groovy script with a detailed action annotation
        String actionName = "testDetailedAction";
        String groovyScript =
            "import org.apache.unomi.api.services.EventService\n" +
            "import org.apache.unomi.groovy.actions.annotations.Action\n" +
            "import org.apache.unomi.groovy.actions.annotations.Parameter\n\n" +
            "@Action(\n" +
            "    id = \"testDetailedAction\",\n" +
            "    name = \"Test Detailed Action\",\n" +
            "    description = \"A detailed action for testing annotations\",\n" +
            "    actionExecutor = \"groovy:testDetailedAction\",\n" +
            "    systemTags = [\"test\", \"groovy\"],\n" +
            "    hidden = true,\n" +
            "    parameters = [\n" +
            "        @Parameter(id = \"stringParam\", type = \"string\", multivalued = false),\n" +
            "        @Parameter(id = \"intParam\", type = \"integer\", multivalued = true),\n" +
            "        @Parameter(id = \"boolParam\", type = \"boolean\", multivalued = false)\n" +
            "    ]\n" +
            ")\n" +
            "def execute() {\n" +
            "    logger.info(\"Executing detailed test action\")\n" +
            "    return EventService.NO_CHANGE\n" +
            "}";

        // Save the action
        contextManager.executeAsTenant(TENANT_1, () -> {
            groovyActionsService.save(actionName, groovyScript);

            // Verify action type is created with all the details
            ActionType actionType = definitionsService.getActionType(actionName);
            assertNotNull("Action type should be created", actionType);

            // Check metadata
            assertEquals("Action name should match", "Test Detailed Action", actionType.getMetadata().getName());
            assertEquals("Action description should match", "A detailed action for testing annotations",
                        actionType.getMetadata().getDescription());
            assertEquals("Action executor should match", "groovy:testDetailedAction",
                        actionType.getActionExecutor());
            assertTrue("Action should be hidden", actionType.getMetadata().isHidden());

            // Check system tags
            Set<String> systemTags = actionType.getMetadata().getSystemTags();
            assertTrue("Should have 'test' system tag", systemTags.contains("test"));
            assertTrue("Should have 'groovy' system tag", systemTags.contains("groovy"));

            // Check parameters
            assertEquals("Should have 3 parameters", 3, actionType.getParameters().size());

            // Check parameters by id
            Map<String, org.apache.unomi.api.Parameter> paramsById = new HashMap<>();
            for (org.apache.unomi.api.Parameter param : actionType.getParameters()) {
                paramsById.put(param.getId(), param);
            }

            assertTrue("Should have stringParam", paramsById.containsKey("stringParam"));
            org.apache.unomi.api.Parameter stringParam = paramsById.get("stringParam");
            assertEquals("String parameter type should match", "string", stringParam.getType());
            assertFalse("String parameter should not be multivalued", stringParam.isMultivalued());

            assertTrue("Should have intParam", paramsById.containsKey("intParam"));
            org.apache.unomi.api.Parameter intParam = paramsById.get("intParam");
            assertEquals("Integer parameter type should match", "integer", intParam.getType());
            assertTrue("Integer parameter should be multivalued", intParam.isMultivalued());

            assertTrue("Should have boolParam", paramsById.containsKey("boolParam"));
            org.apache.unomi.api.Parameter boolParam = paramsById.get("boolParam");
            assertEquals("Boolean parameter type should match", "boolean", boolParam.getType());
            assertFalse("Boolean parameter should not be multivalued", boolParam.isMultivalued());

            // Clean up
            groovyActionsService.remove(actionName);
            assertNull("Action should be removed", definitionsService.getActionType(actionName));
        });
    }

    @Test
    public void testMultiTenantIsolation() {
        // Create a second tenant for testing isolation
        final String TENANT_2 = "tenant2";

        contextManager.executeAsSystem(() -> {
            tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));
            return null;
        });

        // Create a simple action
        String actionName = "isolatedAction";
        String tenant1Script =
            "import org.apache.unomi.api.services.EventService\n" +
            "import org.apache.unomi.groovy.actions.annotations.Action\n\n" +
            "@Action(id = \"isolatedAction\", actionExecutor = \"groovy:isolatedAction\")\n" +
            "def execute() {\n" +
            "    return EventService.NO_CHANGE\n" +
            "}";

        String tenant2Script =
            "import org.apache.unomi.api.services.EventService\n" +
            "import org.apache.unomi.groovy.actions.annotations.Action\n\n" +
            "@Action(id = \"isolatedAction\", actionExecutor = \"groovy:isolatedAction\")\n" +
            "def execute() {\n" +
            "    return EventService.SESSION_UPDATED\n" +
            "}";

        try {
            // Save actions for each tenant
            contextManager.executeAsTenant(TENANT_1, () -> {
                groovyActionsService.save(actionName, tenant1Script);
                assertNotNull("Action should be registered for tenant1",
                             groovyActionsService.getGroovyCodeSource(actionName));
            });

            contextManager.executeAsTenant(TENANT_2, () -> {
                groovyActionsService.save(actionName, tenant2Script);
                assertNotNull("Action should be registered for tenant2",
                             groovyActionsService.getGroovyCodeSource(actionName));
            });

            // Create a dispatcher to test execution
            GroovyActionDispatcher dispatcher = new GroovyActionDispatcher();
            dispatcher.setTracerService(tracerService);
            dispatcher.setGroovyActionsService(groovyActionsService);

            Event event = new Event();
            org.apache.unomi.api.actions.Action action = new org.apache.unomi.api.actions.Action();
            action.setActionTypeId(actionName);

            // Test execution in tenant1
            final int[] tenant1Result = new int[1];
            contextManager.executeAsTenant(TENANT_1, () -> {
                try {
                    tenant1Result[0] = dispatcher.execute(action, event, actionName);
                } catch (Exception e) {
                    fail("Failed to execute action in tenant1: " + e.getMessage());
                }
            });

            // Test execution in tenant2
            final int[] tenant2Result = new int[1];
            contextManager.executeAsTenant(TENANT_2, () -> {
                try {
                    tenant2Result[0] = dispatcher.execute(action, event, actionName);
                } catch (Exception e) {
                    fail("Failed to execute action in tenant2: " + e.getMessage());
                }
            });

            // Verify different results
            assertEquals("Tenant1 action should return NO_CHANGE", EventService.NO_CHANGE, tenant1Result[0]);
            assertEquals("Tenant2 action should return SESSION_UPDATED", EventService.SESSION_UPDATED, tenant2Result[0]);

            // Check tenant isolation - remove tenant1's action
            contextManager.executeAsTenant(TENANT_1, () -> {
                // Remove tenant1's action
                groovyActionsService.remove(actionName);

                // Verify tenant1's action is gone
                assertNull("Tenant1's action should be removed",
                          groovyActionsService.getGroovyCodeSource(actionName));
            });

            // Verify tenant2's action is still available
            contextManager.executeAsTenant(TENANT_2, () -> {
                // Tenant2's action should still be available
                assertNotNull("Tenant2's action should still be available",
                           groovyActionsService.getGroovyCodeSource(actionName));

                // Cleanup
                groovyActionsService.remove(actionName);
            });

        } finally {
            // Clean up tenant2
            contextManager.executeAsSystem(() -> {
                tenantService.deleteTenant(TENANT_2);
                return null;
            });
        }
    }
}

