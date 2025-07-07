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
package org.apache.unomi.services.impl.goals;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoalsServiceImplTest {

    private TestTenantService tenantService;
    private KarafSecurityService securityService;
    private ExecutionContextManagerImpl executionContextManager;
    private GoalsServiceImpl goalsService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private RulesService rulesService;
    private EventServiceImpl eventService;
    private SchedulerService schedulerService;
    private ConditionValidationService conditionValidationService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    @Mock
    private BundleContext bundleContext;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        TracerService tracerService = TestHelper.createTracerService();
        tenantService = new TestTenantService();
        tenantService.setCurrentTenantId("test-tenant");
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
        conditionValidationService = TestHelper.createConditionValidationService();
        // Mock bundle context
        bundleContext = TestHelper.createMockBundleContext();
        schedulerService = TestHelper.createSchedulerService("goals-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);
        // Create scheduler service using TestHelper
        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        // Initialize mocked services using TestHelper
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);
        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));
        ActionType setPropertyAction = TestHelper.createActionType("setPropertyAction", "setPropertyActionExecutor");
        definitionsService.setActionType(setPropertyAction);
        ActionType sendEventAction = TestHelper.createActionType("sendEventAction", "sendEventActionExecutor");
        definitionsService.setActionType(sendEventAction);
        eventService = TestHelper.createEventService(persistenceService, bundleContext, definitionsService, tenantService, tracerService);
        rulesService = TestHelper.createRulesService(persistenceService, bundleContext, schedulerService, definitionsService, eventService, executionContextManager, tenantService, conditionValidationService, multiTypeCacheService);

        // Set the services
        goalsService = new GoalsServiceImpl();
        goalsService.setPersistenceService(persistenceService);
        goalsService.setDefinitionsService(definitionsService);
        goalsService.setRulesService(rulesService);
        goalsService.setConditionValidationService(conditionValidationService);
        goalsService.setTracerService(tracerService);
        goalsService.setCacheService(multiTypeCacheService);
        goalsService.setContextManager(executionContextManager);

        // Mock action type for goal rules
        ActionType goalActionType = new ActionType() {
            private Metadata metadata = new Metadata();
            @Override
            public String getItemId() {
                return "goalMatchedAction";
            }
            @Override
            public String getItemType() {
                return "actionType";
            }
            @Override
            public Metadata getMetadata() {
                return metadata;
            }
            @Override
            public void setMetadata(Metadata metadata) {
                this.metadata = metadata;
            }
            @Override
            public Long getVersion() {
                return 1L;
            }
        };
        goalActionType.getMetadata().setId("goalMatchedAction");
        definitionsService.setActionType(goalActionType);
    }

    @After
    public void tearDown() throws Exception {
        // Stop scheduler service
        if (schedulerService != null && schedulerService instanceof SchedulerServiceImpl) {
            ((SchedulerServiceImpl) schedulerService).preDestroy();
        }

        // Clear cache by clearing each tenant
        if (multiTypeCacheService != null) {
            multiTypeCacheService.clear("test-tenant");
            multiTypeCacheService.clear("system");
        }

        // Clear persistence service data if possible
        if (persistenceService != null && persistenceService instanceof InMemoryPersistenceServiceImpl) {
            // For test cleanup, we'll pass null which is accepted by the implementation for purging all data
            ((InMemoryPersistenceServiceImpl) persistenceService).purge((java.util.Date)null);
        }

        // Reset tenant context
        if (tenantService != null) {
            tenantService.setCurrentTenantId(null);
        }

        // Null out references to help with garbage collection
        tenantService = null;
        securityService = null;
        executionContextManager = null;
        goalsService = null;
        persistenceService = null;
        definitionsService = null;
        rulesService = null;
        eventService = null;
        schedulerService = null;
        conditionValidationService = null;
        multiTypeCacheService = null;
        bundleContext = null;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetGoalWithInvalidStartEvent() {
        // Create a goal with invalid start event
        Goal goal = new Goal();
        goal.setMetadata(new Metadata());
        goal.getMetadata().setId("testGoal");
        goal.getMetadata().setEnabled(true);

        Condition startEvent = new Condition();
        goal.setStartEvent(startEvent);

        // Mock validation to return errors with enhanced context
        List<ConditionValidationService.ValidationError> errors = new ArrayList<>();
        Map<String, Object> context = new HashMap<>();
        context.put("location", "startEvent");
        context.put("parameterType", "string");
        context.put("actualValue", 123);
        errors.add(new ConditionValidationService.ValidationError(
            "testParam",
            "Invalid parameter value",
            ConditionValidationService.ValidationErrorType.INVALID_VALUE,
            "testGoal",
            "startEvent",
            context,
            null
        ));

        // Should throw IllegalArgumentException with detailed message
        goalsService.setGoal(goal);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetGoalWithInvalidTargetEvent() {
        // Create a goal with invalid target event
        Goal goal = new Goal();
        goal.setMetadata(new Metadata());
        goal.getMetadata().setId("testGoal");
        goal.getMetadata().setEnabled(true);

        Condition targetEvent = new Condition();
        goal.setTargetEvent(targetEvent);

        // Mock validation to return errors with enhanced context
        List<ConditionValidationService.ValidationError> errors = new ArrayList<>();
        Map<String, Object> context = new HashMap<>();
        context.put("location", "targetEvent");
        context.put("parameterType", "integer");
        context.put("actualValue", "not a number");
        errors.add(new ConditionValidationService.ValidationError(
            "testParam",
            "Invalid parameter value",
            ConditionValidationService.ValidationErrorType.INVALID_VALUE,
            "testGoal",
            "targetEvent",
            context,
            null
        ));
        // Should throw IllegalArgumentException with detailed message
        goalsService.setGoal(goal);
    }

    @Test
    public void testSetGoalWithValidEvents() {
        // Create a goal with valid events
        Goal goal = new Goal();
        goal.setMetadata(new Metadata());
        goal.getMetadata().setId("testGoal");
        goal.getMetadata().setEnabled(true);

        Condition startEvent = new Condition();
        startEvent.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        startEvent.setParameter("propertyName", "profileProperty");
        startEvent.setParameter("comparisonOperator", "exists");
        Condition targetEvent = new Condition();
        targetEvent.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        targetEvent.setParameter("propertyName", "profileProperty");
        targetEvent.setParameter("comparisonOperator", "exists");
        goal.setStartEvent(startEvent);
        goal.setTargetEvent(targetEvent);

        // Should not throw any exceptions
        goalsService.setGoal(goal);
    }

    @Test
    public void testSetGoalWithNestedConditions() {
        // Create a goal with nested conditions
        Goal goal = new Goal();
        goal.setMetadata(new Metadata());
        goal.getMetadata().setId("testGoal");
        goal.getMetadata().setEnabled(true);

        // Create parent condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("booleanCondition");
        parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        parentCondition.setParameter("operator", "and");

        // Create child condition
        Condition childCondition = new Condition();
        childCondition.setConditionTypeId("profilePropertyCondition");
        childCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        childCondition.setParameter("propertyName", "profileProperty");
        childCondition.setParameter("comparisonOperator", "exists");

        // Set up nested structure
        List<Condition> subConditions = new ArrayList<>();
        subConditions.add(childCondition);
        parentCondition.setParameter("subConditions", subConditions);

        goal.setStartEvent(parentCondition);

        // Should not throw any exceptions
        goalsService.setGoal(goal);
    }
}
