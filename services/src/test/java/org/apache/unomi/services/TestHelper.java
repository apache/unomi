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

import org.apache.commons.io.FileUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.services.cache.MultiTypeCacheService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.api.tenants.AuditService;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.impl.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.KarafSecurityService;
import org.apache.unomi.services.impl.TestRequestTracer;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.rules.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
import org.apache.unomi.services.impl.validation.ConditionValidationServiceImpl;
import org.apache.unomi.services.impl.validation.validators.*;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.TraceNode;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

public class TestHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHelper.class);
    private static final int MAX_RETRIES = 20;
    private static final long RETRY_DELAY_MS = 100;

    /**
     * Creates a security service instance for testing purposes.
     * Initializes a new KarafSecurityService with audit service and default configuration.
     *
     * @return A configured KarafSecurityService instance
     */
    public static KarafSecurityService createSecurityService() {
        KarafSecurityService securityService = new KarafSecurityService();
        AuditService auditService = new AuditServiceImpl();
        securityService.setTenantAuditService(auditService);
        securityService.setConfiguration(new SecurityServiceConfiguration());
        securityService.init();
        return securityService;
    }

    /**
     * Creates an execution context manager for testing purposes.
     * Sets up an ExecutionContextManagerImpl with the provided security service.
     *
     * @param securityService The security service to use in the context manager
     * @return A configured ExecutionContextManagerImpl instance
     */
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
            ExecutionContextManager executionContextManager, BundleContext bundleContext) {
        return createSchedulerService(persistenceService, executionContextManager, bundleContext, true);
    }
    public static SchedulerService createSchedulerService(
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager, BundleContext bundleContext, boolean construct) {
        org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl schedulerService =
            new org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl();
        schedulerService.setPersistenceService(persistenceService);
        schedulerService.setBundleContext(bundleContext);
        schedulerService.setThreadPoolSize(4); // Ensure enough threads for parallel execution
        schedulerService.setExecutorNode(true);
        schedulerService.setNodeId("test-scheduler-node");
        schedulerService.setPurgeTaskEnabled(false); // Disable purge task by default for tests
        if (construct) {
            schedulerService.postConstruct();
        }
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

    /**
     * Test implementation of TracerService for testing purposes.
     * Provides basic tracing functionality with a test request tracer.
     */
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
        public TraceNode getTraceNode() {
            return requestTracer.getTraceNode();
        }
    }

    /**
     * Creates a test action type with specified configuration.
     * Initializes an ActionType with the provided ID and action executor.
     *
     * @param id The unique identifier for the action type
     * @param actionExecutor The name of the action executor to use
     * @return A configured ActionType instance
     */
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

    /**
     * Sets up common test data in the tenant service.
     * Creates standard test tenants with basic configuration.
     *
     * @param tenantService The tenant service to populate with test data
     */
    public static void setupCommonTestData(TenantService tenantService) {
        // Create standard test tenants
        tenantService.createTenant("system", Collections.singletonMap("description", "System tenant"));
        tenantService.createTenant("tenant1", Collections.singletonMap("description", "Tenant 1"));
        tenantService.createTenant("tenant2", Collections.singletonMap("description", "Tenant 2"));
    }

    /**
     * Creates a mock bundle context for testing purposes.
     * Sets up a mock BundleContext with basic behavior for bundle operations.
     *
     * @return A configured mock BundleContext instance
     */
    public static BundleContext createMockBundleContext() {
        BundleContext bundleContext = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.findEntries(eq("META-INF/cxs/rules"), eq("*.json"), eq(true))).thenReturn(null);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);
        return bundleContext;
    }

    /**
     * Creates an event service instance for testing purposes.
     * Initializes an EventServiceImpl with all required dependencies.
     *
     * @param persistenceService The persistence service to use
     * @param bundleContext The bundle context to use
     * @param definitionsService The definitions service to use
     * @param tenantService The tenant service to use
     * @param tracerService The tracer service to use
     * @return A configured EventServiceImpl instance
     */
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

    /**
     * Creates a test task executor with specified behavior.
     * The executor will run the provided execution and handle success/failure callbacks.
     *
     * @param taskType The type identifier for the task executor
     * @param execution The runnable containing the execution logic
     * @return A configured TaskExecutor instance
     */
    public static TaskExecutor createTestExecutor(String taskType, Runnable execution) {
        return new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                try {
                    execution.run();
                    callback.complete();
                } catch (Exception e) {
                    callback.fail(e.getMessage());
                }
            }
        };
    }

    /**
     * Creates a test task with specified configuration.
     * Initializes a new ScheduledTask with the provided parameters and default settings.
     *
     * @param taskId The unique identifier for the task
     * @param taskType The type of task to create
     * @param persistent Whether the task should be persistent
     * @return A configured ScheduledTask instance
     */
    public static ScheduledTask createTestTask(String taskId, String taskType, boolean persistent) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(taskId);
        task.setTaskType(taskType);
        task.setEnabled(true);
        task.setPersistent(persistent);
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        return task;
    }

    /**
     * Creates a test scheduler node with specified configuration.
     * Sets up a SchedulerServiceImpl instance with the provided parameters and initializes it.
     *
     * @param persistenceService The persistence service to use
     * @param nodeId The unique identifier for this node
     * @param executorNode Whether this node should execute tasks
     * @param lockTimeout The timeout duration for task locks (in milliseconds)
     * @return A configured SchedulerServiceImpl instance
     */
    public static SchedulerServiceImpl createTestNode(PersistenceService persistenceService, String nodeId, boolean executorNode, long lockTimeout) {
        SchedulerServiceImpl node = new SchedulerServiceImpl();
        if (lockTimeout > 0) {
            node.setLockTimeout(lockTimeout);
        }
        node.setPersistenceService(persistenceService);
        node.setExecutorNode(executorNode);
        node.setThreadPoolSize(2);
        node.setPurgeTaskEnabled(false);
        node.setNodeId(nodeId);
        node.postConstruct();
        return node;
    }

    /**
     * Cleans up the default storage directory used in tests.
     * Attempts to delete the directory with retries in case of failures.
     *
     * @param maxRetries The maximum number of deletion attempts
     * @throws RuntimeException if the directory cannot be deleted after all retries
     */
    public static void cleanDefaultStorageDirectory(int maxRetries) {
        Path defaultStorageDir = Paths.get(InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR).toAbsolutePath().normalize();
        int count = 0;
        while (Files.exists(defaultStorageDir) && count < maxRetries) {
        try {
            FileUtils.deleteDirectory(defaultStorageDir.toFile());
        } catch (IOException e) {
            LOGGER.warn("Error deleting default storage directory, will retry in 1 second", e);
        }
        try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            count++;
        }
        if (count == maxRetries) {
            throw new RuntimeException("Failed to delete default storage directory after " + maxRetries + " retries");
        }
    }

    /**
     * Generic retry method that will retry an operation until it succeeds or reaches max retries.
     * @param operation The operation to retry that returns a result
     * @param successCondition The predicate to test if the operation was successful
     * @param <T> The type of result returned by the operation
     * @return The result of the successful operation
     * @throws RuntimeException if max retries are reached without success
     */
    public static <T> T retryUntil(Supplier<T> operation, Predicate<T> successCondition) {
        int attempts = 0;
        T result = null;
        boolean success = false;

        while (!success && attempts < MAX_RETRIES) {
            result = operation.get();
            success = successCondition.test(result);

            if (!success) {
                attempts++;
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", e);
                }
            }
        }

        if (!success) {
            throw new RuntimeException("Operation failed after " + MAX_RETRIES + " attempts");
        }

        return result;
    }

}
