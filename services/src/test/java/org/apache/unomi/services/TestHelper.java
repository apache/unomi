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
package org.apache.unomi.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tenants.AuditService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.impl.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.KarafSecurityService;
import org.apache.unomi.services.impl.TestRequestTracer;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.rules.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
import org.apache.unomi.services.impl.validation.ConditionValidationServiceImpl;
import org.apache.unomi.services.impl.validation.validators.*;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.mockito.Mockito.*;

public class TestHelper {

    public static KarafSecurityService createSecurityService() {
        KarafSecurityService securityService = new KarafSecurityService();
        AuditService auditService = new AuditServiceImpl();
        securityService.setTenantAuditService(auditService);
        securityService.setConfiguration(new SecurityServiceConfiguration());
        securityService.init();
        return securityService;
    }

    public static ExecutionContextManagerImpl createExecutionContextManager(KarafSecurityService securityService) {
        ExecutionContextManagerImpl executionContextManager = new ExecutionContextManagerImpl();
        executionContextManager.setSecurityService(securityService);
        return executionContextManager;
    }

    public static DefinitionsServiceImpl createDefinitionService(
        PersistenceService persistenceService,
        BundleContext bundleContext,
        SchedulerService schedulerService,
        MultiTypeCacheService multiTypeCacheService,
        ExecutionContextManager executionContextManager,
        TenantService tenantService,
        ConditionValidationService conditionValidationService
    ) {
        DefinitionsServiceImpl definitionsService = new DefinitionsServiceImpl();
        TracerService tracerService = createTracerService();
        definitionsService.setPersistenceService(persistenceService);
        definitionsService.setBundleContext(bundleContext);
        definitionsService.setSchedulerService(schedulerService);
        definitionsService.setCacheService(multiTypeCacheService);
        definitionsService.setContextManager(executionContextManager);
        definitionsService.setTenantService(tenantService);
        definitionsService.setConditionValidationService(conditionValidationService);
        definitionsService.setTracerService(tracerService);
        definitionsService.postConstruct();
        return definitionsService;
    }

    public static RulesServiceImpl createRulesService(
        PersistenceService persistenceService,
        BundleContext bundleContext,
        SchedulerService schedulerService,
        DefinitionsServiceImpl definitionsService,
        EventServiceImpl eventService,
        ExecutionContextManager executionContextManager,
        TenantService tenantService,
        ConditionValidationService conditionValidationService
    ) {
        RulesServiceImpl rulesService = new RulesServiceImpl();
        TestActionExecutorDispatcher actionExecutorDispatcher = new TestActionExecutorDispatcher(definitionsService, persistenceService);
        actionExecutorDispatcher.setDefaultReturnValue(EventService.PROFILE_UPDATED);

        // Set up tracing
        TracerService tracerService = createTracerService();
        TestRequestTracer tracer = new TestRequestTracer(true);
        actionExecutorDispatcher.setTracer(tracer);

        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setEventService(eventService);
        rulesService.setActionExecutorDispatcher(actionExecutorDispatcher);
        rulesService.setTenantService(tenantService);
        rulesService.setSchedulerService(schedulerService);
        rulesService.setContextManager(executionContextManager);
        rulesService.setConditionValidationService(conditionValidationService);
        rulesService.setTracerService(tracerService);

        // Create and register test action type
        ActionType testActionType = new ActionType();
        testActionType.setItemId("test");
        Metadata actionMetadata = new Metadata();
        actionMetadata.setId("test");
        actionMetadata.setEnabled(true);
        testActionType.setMetadata(actionMetadata);
        testActionType.setActionExecutor("test");
        definitionsService.setActionType(testActionType);

        // Create and register setEventOccurenceCountAction type
        ActionType setEventOccurenceCountActionType = new ActionType();
        setEventOccurenceCountActionType.setItemId("setEventOccurenceCountAction");
        Metadata setEventOccurenceCountMetadata = new Metadata();
        setEventOccurenceCountMetadata.setId("setEventOccurenceCountAction");
        setEventOccurenceCountMetadata.setEnabled(true);
        setEventOccurenceCountActionType.setMetadata(setEventOccurenceCountMetadata);
        setEventOccurenceCountActionType.setActionExecutor("setEventOccurenceCountAction");
        definitionsService.setActionType(setEventOccurenceCountActionType);

        // Initialize rule caches
        rulesService.postConstruct();
        eventService.addEventListenerService(rulesService);

        return rulesService;
    }

    public static SchedulerService createSchedulerService(
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager) {
        org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl schedulerService =
            new org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl();
        schedulerService.setPersistenceService(persistenceService);
        schedulerService.setThreadPoolSize(4); // Ensure enough threads for parallel execution
        schedulerService.setExecutorNode(true);
        schedulerService.setPurgeTaskEnabled(false); // Disable purge task by default for tests
        schedulerService.postConstruct();
        return schedulerService;
    }

    public static ConditionValidationService createConditionValidationService() {
        ConditionValidationServiceImpl conditionValidationService = new ConditionValidationServiceImpl();
        List<ValueTypeValidator> validators = Arrays.asList(
                new StringValueTypeValidator(),
                new IntegerValueTypeValidator(),
                new LongValueTypeValidator(),
                new FloatValueTypeValidator(),
                new DoubleValueTypeValidator(),
                new BooleanValueTypeValidator(),
                new DateValueTypeValidator(),
                new ComparisonOperatorValueTypeValidator(),
                new ConditionValueTypeValidator()
        );
        conditionValidationService.setBuiltInValidators(validators);
        return conditionValidationService;
    }

    public static TracerService createTracerService() {
        return new TestTracerService();
    }

    private static class TestTracerService implements TracerService {
        private final RequestTracer requestTracer = new TestRequestTracer(true);

        @Override
        public RequestTracer getCurrentTracer() {
            return requestTracer;
        }

        @Override
        public void enableTracing() {
            requestTracer.setEnabled(true);
        }

        @Override
        public void disableTracing() {
            requestTracer.setEnabled(false);
        }

        @Override
        public boolean isTracingEnabled() {
            return requestTracer.isEnabled();
        }

        @Override
        public String getTraceAsJson() {
            return requestTracer.getTraceAsJson();
        }
    }

    public static ActionType createActionType(String id, String actionExecutor) {
        ActionType actionType = new ActionType() {
            private Metadata metadata = new Metadata();
            @Override
            public String getItemId() {
                return id;
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
        Metadata actionMetadata = new Metadata();
        actionMetadata.setId(id);
        actionMetadata.setEnabled(true);
        actionType.setMetadata(actionMetadata);
        if (actionExecutor != null) {
            actionType.setActionExecutor(actionExecutor);
        }
        return actionType;
    }

    public static void setupCommonTestData(TenantService tenantService) {
        // Create standard test tenants
        tenantService.createTenant("system", Collections.singletonMap("description", "System tenant"));
        tenantService.createTenant("tenant1", Collections.singletonMap("description", "Tenant 1"));
        tenantService.createTenant("tenant2", Collections.singletonMap("description", "Tenant 2"));
    }

    public static BundleContext createMockBundleContext() {
        BundleContext bundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.findEntries(eq("META-INF/cxs/rules"), eq("*.json"), eq(true))).thenReturn(null);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);
        return bundleContext;
    }

    public static EventServiceImpl createEventService(
            PersistenceService persistenceService,
            BundleContext bundleContext,
            DefinitionsServiceImpl definitionsService,
            TenantService tenantService,
            TracerService tracerService) {
        EventServiceImpl eventService = new EventServiceImpl();
        eventService.setBundleContext(bundleContext);
        eventService.setPersistenceService(persistenceService);
        eventService.setDefinitionsService(definitionsService);
        eventService.setTenantService(tenantService);
        eventService.setTracerService(tracerService);
        return eventService;
    }

    public static void setupSegmentActionTypes(DefinitionsServiceImpl definitionsService) {
        // Register the evaluateProfileSegmentsAction type
        ActionType actionType = new ActionType();
        actionType.setItemId("evaluateProfileSegmentsAction");
        actionType.setActionExecutor("evaluateProfileSegments");

        Metadata metadata = new Metadata();
        metadata.setId("evaluateProfileSegmentsAction");
        metadata.setName("Evaluate Profile Segments");
        metadata.setDescription("Evaluates the segments for a profile and updates the profile with the matching segments");
        metadata.setSystemTags(Collections.singleton("profileTags"));
        metadata.setEnabled(true);
        metadata.setHidden(false);
        actionType.setMetadata(metadata);

        definitionsService.setActionType(actionType);

        // Register the profileUpdatedEventCondition type
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("profileUpdatedEventCondition");
        conditionType.setConditionEvaluator("profileUpdatedEventConditionEvaluator");
        conditionType.setQueryBuilder("eventTypeConditionESQueryBuilder");

        Metadata conditionMetadata = new Metadata();
        conditionMetadata.setId("profileUpdatedEventCondition");
        conditionMetadata.setName("Profile Updated Event");
        conditionMetadata.setDescription("Condition to match profile updated events");
        conditionMetadata.setSystemTags(new HashSet<>(Arrays.asList("profileTags", "event", "condition", "eventCondition")));
        conditionMetadata.setEnabled(true);
        conditionType.setMetadata(conditionMetadata);

        definitionsService.setConditionType(conditionType);
    }
}
