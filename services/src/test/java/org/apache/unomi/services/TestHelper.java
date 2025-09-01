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
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.TestRequestTracer;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.rules.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.scheduler.PersistenceSchedulerProvider;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.services.impl.scheduler.TaskExecutionManager;
import org.apache.unomi.services.impl.scheduler.TaskExecutorRegistry;
import org.apache.unomi.services.impl.scheduler.TaskHistoryManager;
import org.apache.unomi.services.impl.scheduler.TaskLockManager;
import org.apache.unomi.services.impl.scheduler.TaskMetricsManager;
import org.apache.unomi.services.impl.scheduler.TaskRecoveryManager;
import org.apache.unomi.services.impl.scheduler.TaskStateManager;
import org.apache.unomi.services.impl.scheduler.TaskValidationManager;
import org.apache.unomi.services.common.security.AuditServiceImpl;
import org.apache.unomi.services.impl.validation.ConditionValidationServiceImpl;
import org.apache.unomi.services.impl.validation.validators.*;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.TraceNode;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.services.impl.cluster.ClusterServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        ConditionValidationService conditionValidationService,
        MultiTypeCacheService multiTypeCacheService
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
        rulesService.setCacheService(multiTypeCacheService);

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

    /**
     * Creates a scheduler service instance for testing purposes with ClusterService support.
     *
     * @param nodeId The unique identifier for this node
     * @param persistenceService The persistence service to use
     * @param executionContextManager The execution context manager to use
     * @param bundleContext The bundle context to use
     * @param clusterService The cluster service to use (can be null)
     * @param lockTimeout The lock timeout to use (in milliseconds)
     * @param executorNode Whether this node is an executor node
     * @param construct Whether to call postConstruct on the service
     * @param completedTaskTtlDays The TTL for completed tasks (in days)
     * @return A configured SchedulerServiceImpl instance
     */
    public static SchedulerServiceImpl createSchedulerService(
            String nodeId,
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager,
            BundleContext bundleContext,
            ClusterService clusterService,
            long lockTimeout,
            boolean executorNode,
            boolean construct,
            long completedTaskTtlDays) {

        // Instantiate and wire task manager beans as in blueprint.xml

        // Task Metrics Manager
        TaskMetricsManager taskMetricsManager = new TaskMetricsManager();

        // Task State Manager
        TaskStateManager taskStateManager = new TaskStateManager();

        // Task Executor Registry
        TaskExecutorRegistry taskExecutorRegistry = new TaskExecutorRegistry();

        // Task History Manager
        TaskHistoryManager taskHistoryManager = new TaskHistoryManager();
        taskHistoryManager.setNodeId(nodeId);
        taskHistoryManager.setMetricsManager(taskMetricsManager);

        // Task Lock Manager
        TaskLockManager taskLockManager = new TaskLockManager();
        taskLockManager.setNodeId(nodeId);
        taskLockManager.setLockTimeout(lockTimeout > 0 ? lockTimeout : 10000L);
        taskLockManager.setMetricsManager(taskMetricsManager);

        // Task Execution Manager
        TaskExecutionManager taskExecutionManager = new TaskExecutionManager();
        taskExecutionManager.setNodeId(nodeId);
        taskExecutionManager.setThreadPoolSize(4);
        taskExecutionManager.setStateManager(taskStateManager);
        taskExecutionManager.setLockManager(taskLockManager);
        taskExecutionManager.setMetricsManager(taskMetricsManager);
        taskExecutionManager.setHistoryManager(taskHistoryManager);
        taskExecutionManager.setExecutorRegistry(taskExecutorRegistry);
        taskExecutionManager.initialize(); // Initialize after all dependencies are set

        // Task Recovery Manager
        TaskRecoveryManager taskRecoveryManager = new TaskRecoveryManager();
        taskRecoveryManager.setNodeId(nodeId);
        taskRecoveryManager.setStateManager(taskStateManager);
        taskRecoveryManager.setLockManager(taskLockManager);
        taskRecoveryManager.setMetricsManager(taskMetricsManager);
        taskRecoveryManager.setExecutionManager(taskExecutionManager);
        taskRecoveryManager.setExecutorRegistry(taskExecutorRegistry);

        // Task Validation Manager
        TaskValidationManager taskValidationManager = new TaskValidationManager();

        PersistenceSchedulerProvider persistenceSchedulerProvider = new PersistenceSchedulerProvider();
        persistenceSchedulerProvider.setPersistenceService(persistenceService);
        persistenceSchedulerProvider.setExecutorNode(executorNode);
        persistenceSchedulerProvider.setNodeId(nodeId);
        persistenceSchedulerProvider.setLockManager(taskLockManager);
        persistenceSchedulerProvider.setClusterService(clusterService);
        persistenceSchedulerProvider.setCompletedTaskTtlDays(completedTaskTtlDays);

        SchedulerServiceImpl schedulerService = new SchedulerServiceImpl();
        schedulerService.setMetricsManager(taskMetricsManager);
        schedulerService.setStateManager(taskStateManager);
        schedulerService.setExecutorRegistry(taskExecutorRegistry);
        schedulerService.setHistoryManager(taskHistoryManager);
        schedulerService.setLockManager(taskLockManager);
        schedulerService.setExecutionManager(taskExecutionManager);
        schedulerService.setRecoveryManager(taskRecoveryManager);
        schedulerService.setValidationManager(taskValidationManager);
        schedulerService.setBundleContext(bundleContext);
        schedulerService.setThreadPoolSize(4); // Ensure enough threads for parallel execution
        schedulerService.setExecutorNode(executorNode);
        schedulerService.setNodeId(nodeId);
        schedulerService.setPurgeTaskEnabled(false);
        if (lockTimeout > 0) {
            schedulerService.setLockTimeout(lockTimeout); // Set a default lock timeout for tests
        }

        // Set the persistence provider on the scheduler service (optional dependency)
        if (persistenceSchedulerProvider != null) {
            schedulerService.setPersistenceProvider(persistenceSchedulerProvider);
        }

        // Set the schedulerService on all managers that need it
        taskLockManager.setSchedulerService(schedulerService);
        taskExecutionManager.setSchedulerService(schedulerService);
        taskRecoveryManager.setSchedulerService(schedulerService);

        if (construct) {
            schedulerService.postConstruct();
        }
        return schedulerService;
    }

    /**
     * Creates a scheduler service instance for testing purposes with ClusterService support.
     * Uses default TTL of 30 days for completed tasks.
     *
     * @param nodeId The unique identifier for this node
     * @param persistenceService The persistence service to use
     * @param executionContextManager The execution context manager to use
     * @param bundleContext The bundle context to use
     * @param clusterService The cluster service to use (can be null)
     * @param lockTimeout The lock timeout to use (in milliseconds)
     * @param executorNode Whether this node is an executor node
     * @param construct Whether to call postConstruct on the service
     * @return A configured SchedulerServiceImpl instance
     */
    public static SchedulerServiceImpl createSchedulerService(
            String nodeId,
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager,
            BundleContext bundleContext,
            ClusterService clusterService,
            long lockTimeout,
            boolean executorNode,
            boolean construct) {
        return createSchedulerService(nodeId, persistenceService, executionContextManager, bundleContext, clusterService, lockTimeout, executorNode, construct, 30);
    }

    /**
     * Creates a scheduler service instance without a PersistenceSchedulerProvider for testing optional dependency scenarios.
     *
     * @param nodeId The unique identifier for this node
     * @param persistenceService The persistence service to use
     * @param executionContextManager The execution context manager to use
     * @param bundleContext The bundle context to use
     * @param clusterService The cluster service to use (can be null)
     * @param lockTimeout The lock timeout to use (in milliseconds)
     * @param executorNode Whether this node is an executor node
     * @param construct Whether to call postConstruct on the service
     * @return A configured SchedulerServiceImpl instance without persistence provider
     */
    public static SchedulerServiceImpl createSchedulerServiceWithoutPersistenceProvider(
            String nodeId,
            PersistenceService persistenceService,
            ExecutionContextManager executionContextManager,
            BundleContext bundleContext,
            ClusterService clusterService,
            long lockTimeout,
            boolean executorNode,
            boolean construct) {

        // Create all required managers
        TaskStateManager taskStateManager = new TaskStateManager();
        TaskLockManager taskLockManager = new TaskLockManager();
        TaskExecutionManager taskExecutionManager = new TaskExecutionManager();
        TaskRecoveryManager taskRecoveryManager = new TaskRecoveryManager();
        TaskMetricsManager taskMetricsManager = new TaskMetricsManager();
        TaskHistoryManager taskHistoryManager = new TaskHistoryManager();
        TaskValidationManager taskValidationManager = new TaskValidationManager();
        TaskExecutorRegistry taskExecutorRegistry = new TaskExecutorRegistry();

        // Configure managers
        taskLockManager.setNodeId(nodeId);
        taskLockManager.setLockTimeout(lockTimeout > 0 ? lockTimeout : 10000);
        taskLockManager.setMetricsManager(taskMetricsManager);

        taskHistoryManager.setNodeId(nodeId);
        taskHistoryManager.setMetricsManager(taskMetricsManager);

        taskExecutionManager.setNodeId(nodeId);
        taskExecutionManager.setThreadPoolSize(4);
        taskExecutionManager.setStateManager(taskStateManager);
        taskExecutionManager.setLockManager(taskLockManager);
        taskExecutionManager.setMetricsManager(taskMetricsManager);
        taskExecutionManager.setHistoryManager(taskHistoryManager);
        taskExecutionManager.setExecutorRegistry(taskExecutorRegistry);
        taskExecutionManager.initialize();

        taskRecoveryManager.setNodeId(nodeId);
        taskRecoveryManager.setStateManager(taskStateManager);
        taskRecoveryManager.setLockManager(taskLockManager);
        taskRecoveryManager.setMetricsManager(taskMetricsManager);
        taskRecoveryManager.setExecutionManager(taskExecutionManager);
        taskRecoveryManager.setExecutorRegistry(taskExecutorRegistry);

        // Create scheduler service
        SchedulerServiceImpl schedulerService = new SchedulerServiceImpl();
        schedulerService.setBundleContext(bundleContext);
        schedulerService.setThreadPoolSize(4);
        schedulerService.setExecutorNode(executorNode);
        schedulerService.setNodeId(nodeId);
        schedulerService.setPurgeTaskEnabled(false);
        if (lockTimeout > 0) {
            schedulerService.setLockTimeout(lockTimeout);
        }

        // Set all required managers
        schedulerService.setStateManager(taskStateManager);
        schedulerService.setLockManager(taskLockManager);
        schedulerService.setExecutionManager(taskExecutionManager);
        schedulerService.setRecoveryManager(taskRecoveryManager);
        schedulerService.setMetricsManager(taskMetricsManager);
        schedulerService.setHistoryManager(taskHistoryManager);
        schedulerService.setValidationManager(taskValidationManager);
        schedulerService.setExecutorRegistry(taskExecutorRegistry);

        // Note: persistenceProvider is intentionally not set to test optional dependency

        // Set the schedulerService on all managers that need it
        taskLockManager.setSchedulerService(schedulerService);
        taskExecutionManager.setSchedulerService(schedulerService);
        taskRecoveryManager.setSchedulerService(schedulerService);

        if (construct) {
            schedulerService.postConstruct();
        }
        return schedulerService;
    }

    public static ConditionValidationService createConditionValidationService() {
        ConditionValidationServiceImpl conditionValidationService = new ConditionValidationServiceImpl();
        List<ValueTypeValidator> validators = new ArrayList<>();
        validators.add(new StringValueTypeValidator());
        validators.add(new IntegerValueTypeValidator());
        validators.add(new LongValueTypeValidator());
        validators.add(new FloatValueTypeValidator());
        validators.add(new DoubleValueTypeValidator());
        validators.add(new BooleanValueTypeValidator());
        validators.add(new DateValueTypeValidator());
        validators.add(new ComparisonOperatorValueTypeValidator());
        validators.add(new ConditionValueTypeValidator());
        conditionValidationService.setBuiltInValidators(validators);
        return conditionValidationService;
    }

    public static TracerService createTracerService() {
        return new TestTracerService();
    }

    /**
     * Creates a cluster service instance for testing purposes.
     * Initializes a new ClusterServiceImpl with the specified persistence service and node ID.
     *
     * NOTE: Due to circular dependency between ClusterService and SchedulerService,
     * when using both services together:
     * 1. Create the ClusterService first using this method
     * 2. Create the SchedulerService using createSchedulerService() and pass the ClusterService
     * 3. If tasks were not initialized during startup, call clusterService.initializeScheduledTasks()
     *
     * @param persistenceService The persistence service to use
     * @param nodeId The unique identifier for this node
     * @param bundleContext The bundle context to use for service trackers (can be null)
     * @return A configured ClusterServiceImpl instance
     */
    public static ClusterServiceImpl createClusterService(PersistenceService persistenceService, String nodeId, BundleContext bundleContext) {
        return createClusterService(persistenceService, nodeId, "127.0.0.1", "127.0.0.1", bundleContext);
    }


    /**
     * Creates a cluster service instance for testing purposes with custom addresses and bundle context.
     * Initializes a new ClusterServiceImpl with the specified persistence service, node ID, addresses, and bundle context.
     *
     * NOTE: Due to circular dependency between ClusterService and SchedulerService,
     * when using both services together:
     * 1. Create the ClusterService first using this method
     * 2. Create the SchedulerService using createSchedulerService() and pass the ClusterService
     * 3. If tasks were not initialized during startup, call clusterService.initializeScheduledTasks()
     *
     * @param persistenceService The persistence service to use
     * @param nodeId The unique identifier for this node
     * @param publicAddress The public address for the node
     * @param internalAddress The internal address for the node
     * @param bundleContext The bundle context to use for service trackers (can be null)
     * @return A configured ClusterServiceImpl instance
     */
    public static ClusterServiceImpl createClusterService(
            PersistenceService persistenceService,
            String nodeId,
            String publicAddress,
            String internalAddress,
            BundleContext bundleContext) {
        ClusterServiceImpl clusterService = new ClusterServiceImpl();
        clusterService.setPersistenceService(persistenceService);
        clusterService.setPublicAddress(publicAddress);
        clusterService.setInternalAddress(internalAddress);
        clusterService.setNodeStatisticsUpdateFrequency(60000);
        clusterService.setNodeId(nodeId);

        return clusterService;
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

    /**
     * Common tearDown method to be used by test classes to clean up resources.
     * This centralizes the common teardown logic to reduce duplication.
     *
     * @param schedulerService The scheduler service instance to stop
     * @param multiTypeCacheService The cache service to clear
     * @param persistenceService The persistence service to purge
     * @param tenantService The tenant service to reset
     * @param tenantIds Array of tenant IDs to clear from the cache
     * @throws Exception If an error occurs during teardown
     */
    public static void tearDown(
            org.apache.unomi.api.services.SchedulerService schedulerService,
            org.apache.unomi.api.services.cache.MultiTypeCacheService multiTypeCacheService,
            org.apache.unomi.persistence.spi.PersistenceService persistenceService,
            org.apache.unomi.api.tenants.TenantService tenantService,
            String... tenantIds) throws Exception {

        // Stop scheduler service
        if (schedulerService != null && schedulerService instanceof org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl) {
            ((org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl) schedulerService).preDestroy();
        }

        // Clear cache by clearing each tenant
        if (multiTypeCacheService != null && tenantIds != null) {
            for (String tenantId : tenantIds) {
                if (tenantId != null) {
                    multiTypeCacheService.clear(tenantId);
                }
            }
        }

        // Clear persistence service data if possible
        if (persistenceService != null && persistenceService instanceof org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl) {
            ((org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl) persistenceService).purge((java.util.Date)null);
        }

        // Reset tenant context
        if (tenantService != null && tenantService instanceof org.apache.unomi.services.impl.TestTenantService) {
            ((org.apache.unomi.services.impl.TestTenantService) tenantService).setCurrentTenantId(null);
        }
    }

    /**
     * Helper method that nulls out service references to help with garbage collection.
     * Pass the objects you want to null out and they will be collected by the garbage collector.
     * This is a no-op method beyond accepting references, but it's organized to be clear
     * and document the intention of discarding object references.
     *
     * @param objects The objects to be nulled out
     */
    public static void cleanupReferences(Object... objects) {
        // This method doesn't actually need to do anything - by passing the objects as
        // parameters, the calling code is setting those instance variables to null,
        // which is the intended effect. This method is simply a cleaner way to organize
        // the nulling of multiple references.
    }
}
