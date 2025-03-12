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
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
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

        schedulerService = TestHelper.createSchedulerService(persistenceService, contextManager, bundleContext);

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
            
            // Set up a mock ActionExecutorDispatcher to intercept the execution
            ActionExecutorDispatcher originalDispatcher = getActionExecutorDispatcher();
            ActionExecutorDispatcher mockDispatcher = mock(ActionExecutorDispatcher.class);
            
            try {
                // Set up the mock to properly execute our Groovy action
                when(mockDispatcher.execute(any(org.apache.unomi.api.actions.Action.class), any(Event.class)))
                    .thenAnswer(invocation -> {
                        org.apache.unomi.api.actions.Action action = invocation.getArgument(0);
                        Event event = invocation.getArgument(1);
                        
                        // Create a GroovyActionDispatcher to execute the action
                        GroovyActionDispatcher groovyDispatcher = new GroovyActionDispatcher();
                        groovyDispatcher.setGroovyActionsService(groovyActionsService);
                        
                        // Execute using the actionTypeId which should match our action name
                        return groovyDispatcher.execute(action, event, action.getActionTypeId());
                    });
                
                // Replace the dispatcher
                setActionExecutorDispatcher(mockDispatcher);
                
                // Create an event and actions with different parameters
                Event event = new Event();
                event.setEventType("test");
                event.setScope("testScope");
                
                // Test 1: SESSION_UPDATED return value
                org.apache.unomi.api.actions.Action action1 = new org.apache.unomi.api.actions.Action();
                action1.setActionTypeId(actionName);
                action1.setParameter("returnType", "SESSION_UPDATED");
                action1.setParameter("shouldFail", false);
                
                int result1 = mockDispatcher.execute(action1, event);
                assertEquals("Action should return SESSION_UPDATED", EventService.SESSION_UPDATED, result1);
                
                // Test 2: NO_CHANGE return value
                org.apache.unomi.api.actions.Action action2 = new org.apache.unomi.api.actions.Action();
                action2.setActionTypeId(actionName);
                action2.setParameter("returnType", "NO_CHANGE");
                action2.setParameter("shouldFail", false);
                
                int result2 = mockDispatcher.execute(action2, event);
                assertEquals("Action should return NO_CHANGE", EventService.NO_CHANGE, result2);
                
                // Test 3: ERROR return value
                org.apache.unomi.api.actions.Action action3 = new org.apache.unomi.api.actions.Action();
                action3.setActionTypeId(actionName);
                action3.setParameter("returnType", "SESSION_UPDATED");
                action3.setParameter("shouldFail", true);
                
                int result3 = mockDispatcher.execute(action3, event);
                assertEquals("Action should return ERROR", EventService.ERROR, result3);
                
            } finally {
                // Restore the original dispatcher
                setActionExecutorDispatcher(originalDispatcher);
            }
        });
    }
    
    private ActionExecutorDispatcher getActionExecutorDispatcher() {
        try {
            // Get the private field with reflection
            java.lang.reflect.Field field = GroovyActionsServiceImpl.class.getDeclaredField("actionExecutorDispatcher");
            field.setAccessible(true);
            return (ActionExecutorDispatcher) field.get(groovyActionsService);
        } catch (Exception e) {
            fail("Failed to get actionExecutorDispatcher: " + e.getMessage());
            return null;
        }
    }
    
    private void setActionExecutorDispatcher(ActionExecutorDispatcher dispatcher) {
        try {
            // Set the private field with reflection
            java.lang.reflect.Field field = GroovyActionsServiceImpl.class.getDeclaredField("actionExecutorDispatcher");
            field.setAccessible(true);
            field.set(groovyActionsService, dispatcher);
        } catch (Exception e) {
            fail("Failed to set actionExecutorDispatcher: " + e.getMessage());
        }
    }
}
