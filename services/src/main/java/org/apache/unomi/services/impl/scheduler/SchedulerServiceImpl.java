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

package org.apache.unomi.services.impl.scheduler;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.ScheduledTask.TaskStatus;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.api.services.ClusterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.apache.unomi.persistence.spi.QueryBuilderAvailabilityTracker;

/**
 * Implementation of the SchedulerService that provides task scheduling and execution capabilities.
 * This implementation supports:
 * - Persistent and in-memory tasks
 * - Single-node and cluster execution
 * - Task dependencies and waiting queues
 * - Lock management and crash recovery
 * - Execution history and metrics tracking
 * - Pending operations queue for initialization
 *
 * Task Lifecycle:
 * 1. SCHEDULED: Initial state, task is ready to execute
 * 2. WAITING: Task is waiting for dependencies or lock
 * 3. RUNNING: Task is currently executing
 * 4. COMPLETED/FAILED/CANCELLED/CRASHED: Terminal states
 *
 * Lock Management:
 * - Tasks can be configured to allow/disallow parallel execution
 * - Locks are managed differently for persistent and in-memory tasks
 * - Lock timeout mechanism prevents deadlocks
 *
 * Clustering Support:
 * - Tasks can be configured to run on specific nodes or all nodes
 * - Lock ownership prevents duplicate execution
 * - Crash recovery handles node failures
 *
 * Pending Operations:
 * - Operations that require subservices are queued during initialization
 * - Operations are executed once all required services are available
 * - Supports different operation types with appropriate handling
 *
 * @author dgaillard
 */
public class SchedulerServiceImpl implements SchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class.getName());
    private static final long DEFAULT_LOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private static final long DEFAULT_COMPLETED_TASK_TTL_DAYS = 30; // 30 days default retention for completed tasks
    private static final boolean DEFAULT_PURGE_TASK_ENABLED = true;
    private static final int MIN_THREAD_POOL_SIZE = 4;
    private static final int PENDING_OPERATIONS_QUEUE_SIZE = 1000;

    private String nodeId;
    private boolean executorNode;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;
    private long lockTimeout = DEFAULT_LOCK_TIMEOUT;
    private long completedTaskTtlDays = DEFAULT_COMPLETED_TASK_TTL_DAYS;
    private boolean purgeTaskEnabled = DEFAULT_PURGE_TASK_ENABLED;
    private ScheduledTask taskPurgeTask;
    private volatile boolean shutdownNow = false;

    // Service trackers for OSGi services
    /**
     * We use ServiceTrackers instead of Blueprint dependency injection due to a known bug in Apache Aries Blueprint
     * where service dependencies are not properly shut down in reverse order of their initialization.
     *
     * The bug manifests in two ways:
     * 1. Services are not shut down in reverse order of their initialization, causing potential deadlocks
     * 2. The PersistenceService is often shut down before other services that depend on it, leading to timeout waits
     *
     * By using ServiceTrackers, we have explicit control over:
     * - Service lifecycle management
     * - Shutdown order
     * - Service availability checks
     * - Graceful degradation when services become unavailable
     */
    private ServiceTracker<PersistenceService, PersistenceService> persistenceServiceTracker;
    private ServiceTracker<ClusterService, ClusterService> clusterServiceTracker;

    // Keep references for backward compatibility
    private volatile PersistenceService persistenceService;
    private volatile ClusterService clusterService;

    private final Map<String, TaskExecutor> taskExecutors = new ConcurrentHashMap<>();
    private final Map<String, ScheduledTask> nonPersistentTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Queue<ScheduledTask>> waitingNonPersistentTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean checkTasksRunning = new AtomicBoolean(false);

    // Manager instances
    private TaskStateManager stateManager;
    private TaskLockManager lockManager;
    private TaskExecutionManager executionManager;
    private TaskRecoveryManager recoveryManager;
    private TaskMetricsManager metricsManager = new TaskMetricsManager();
    private TaskHistoryManager historyManager;
    private TaskValidationManager validationManager;

    private BundleContext bundleContext;
    private ServiceTracker<TaskExecutor, TaskExecutor> taskExecutorTracker;
    private QueryBuilderAvailabilityTracker queryBuilderAvailabilityTracker;

    private final AtomicBoolean persistenceServiceAvailable = new AtomicBoolean(false);
    private final AtomicBoolean queryBuildersAvailable = new AtomicBoolean(false);
    private final AtomicBoolean servicesInitialized = new AtomicBoolean(false);
    private final CountDownLatch persistenceServiceLatch = new CountDownLatch(1);
    private final CountDownLatch queryBuildersLatch = new CountDownLatch(1);
    private final CountDownLatch servicesInitializedLatch = new CountDownLatch(1);

    // Pending operations queue
    private final Queue<PendingOperation> pendingOperations = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingPendingOperations = new AtomicBoolean(false);

    /**
     * Enum defining the types of pending operations that can be queued
     */
    private enum OperationType {
        REGISTER_TASK_EXECUTOR,
        UNREGISTER_TASK_EXECUTOR,
        SCHEDULE_TASK,
        CANCEL_TASK,
        RETRY_TASK,
        RESUME_TASK,
        RECOVER_CRASHED_TASKS,
        INITIALIZE_TASK_PURGE
    }

    /**
     * Represents a pending operation that needs to be executed once services are available
     */
    private static class PendingOperation {
        private final OperationType type;
        private final Object[] parameters;
        private final long timestamp;
        private final String description;

        public PendingOperation(OperationType type, String description, Object... parameters) {
            this.type = type;
            this.parameters = parameters;
            this.timestamp = System.currentTimeMillis();
            this.description = description;
        }

        public OperationType getType() {
            return type;
        }

        public Object[] getParameters() {
            return parameters;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return String.format("PendingOperation{type=%s, description='%s', timestamp=%d}", 
                type, description, timestamp);
        }
    }

    /**
     * Enum defining valid task state transitions.
     * This ensures tasks move through states in a controlled manner.
     * Invalid transitions will throw IllegalStateException.
     */
    private enum TaskTransition {
        SCHEDULE(TaskStatus.SCHEDULED, EnumSet.of(TaskStatus.WAITING, TaskStatus.RUNNING)),
        EXECUTE(TaskStatus.RUNNING, EnumSet.of(TaskStatus.SCHEDULED, TaskStatus.CRASHED, TaskStatus.WAITING)),
        COMPLETE(TaskStatus.COMPLETED, EnumSet.of(TaskStatus.RUNNING)),
        FAIL(TaskStatus.FAILED, EnumSet.of(TaskStatus.RUNNING)),
        CRASH(TaskStatus.CRASHED, EnumSet.of(TaskStatus.RUNNING)),
        WAIT(TaskStatus.WAITING, EnumSet.of(TaskStatus.SCHEDULED, TaskStatus.RUNNING));

        private final TaskStatus endState;
        private final Set<TaskStatus> validStartStates;

        TaskTransition(TaskStatus endState, Set<TaskStatus> validStartStates) {
            this.endState = endState;
            this.validStartStates = validStartStates;
        }

        /**
         * Checks if a state transition is valid
         * @param from Current task state
         * @param to Target task state
         * @return true if transition is valid
         */
        public static boolean isValidTransition(TaskStatus from, TaskStatus to) {
            return Arrays.stream(values())
                .filter(t -> t.endState == to)
                .anyMatch(t -> t.validStartStates.contains(from));
        }
    }

    /**
     * Checks if all required services are initialized and available
     * @return true if services are ready, false otherwise
     */
    private boolean areServicesReady() {
        return servicesInitialized.get() && 
               persistenceServiceAvailable.get() && 
               queryBuildersAvailable.get() &&
               executionManager != null &&
               !shutdownNow;
    }

    /**
     * Queues an operation to be executed once services are available
     * @param type The type of operation
     * @param description Human-readable description of the operation
     * @param parameters The parameters for the operation
     */
    private void queuePendingOperation(OperationType type, String description, Object... parameters) {
        if (shutdownNow) {
            LOGGER.debug("Shutdown in progress, dropping pending operation: {}", description);
            return;
        }

        PendingOperation operation = new PendingOperation(type, description, parameters);
        pendingOperations.offer(operation);
        LOGGER.debug("Queued pending operation: {}", operation);

        // Try to process pending operations if services are ready
        if (areServicesReady()) {
            processPendingOperations();
        }
    }

    /**
     * Processes all pending operations that were queued before services were ready
     */
    private void processPendingOperations() {
        if (!processingPendingOperations.compareAndSet(false, true)) {
            return; // Already processing
        }

        try {
            if (!areServicesReady()) {
                return; // Services not ready yet
            }

            LOGGER.info("Processing {} pending operations", pendingOperations.size());
            int processedCount = 0;
            int errorCount = 0;

            while (!pendingOperations.isEmpty() && !shutdownNow) {
                PendingOperation operation = pendingOperations.poll();
                if (operation == null) {
                    break;
                }

                try {
                    executePendingOperation(operation);
                    processedCount++;
                    LOGGER.debug("Successfully processed pending operation: {}", operation.getDescription());
                } catch (Exception e) {
                    errorCount++;
                    LOGGER.error("Error processing pending operation: {}", operation.getDescription(), e);
                }
            }

            if (processedCount > 0 || errorCount > 0) {
                LOGGER.info("Processed {} pending operations ({} successful, {} errors)", 
                    processedCount + errorCount, processedCount, errorCount);
            }
        } finally {
            processingPendingOperations.set(false);
        }
    }

    /**
     * Executes a specific pending operation
     * @param operation The operation to execute
     */
    private void executePendingOperation(PendingOperation operation) {
        switch (operation.getType()) {
            case REGISTER_TASK_EXECUTOR:
                TaskExecutor executor = (TaskExecutor) operation.getParameters()[0];
                executionManager.registerTaskExecutor(executor);
                break;

            case UNREGISTER_TASK_EXECUTOR:
                TaskExecutor executorToUnregister = (TaskExecutor) operation.getParameters()[0];
                executionManager.unregisterTaskExecutor(executorToUnregister);
                break;

            case SCHEDULE_TASK:
                ScheduledTask task = (ScheduledTask) operation.getParameters()[0];
                scheduleTaskInternal(task);
                break;

            case CANCEL_TASK:
                String taskId = (String) operation.getParameters()[0];
                cancelTaskInternal(taskId);
                break;

            case RETRY_TASK:
                String retryTaskId = (String) operation.getParameters()[0];
                boolean resetFailureCount = (Boolean) operation.getParameters()[1];
                retryTaskInternal(retryTaskId, resetFailureCount);
                break;

            case RESUME_TASK:
                String resumeTaskId = (String) operation.getParameters()[0];
                resumeTaskInternal(resumeTaskId);
                break;

            case RECOVER_CRASHED_TASKS:
                recoveryManager.recoverCrashedTasks();
                break;

            case INITIALIZE_TASK_PURGE:
                initializeTaskPurgeInternal();
                break;

            default:
                LOGGER.warn("Unknown pending operation type: {}", operation.getType());
        }
    }

    /**
     * Updates task state with validation and persistence
     * @param task The task to update
     * @param newStatus The new status to set
     * @param error Optional error message for failed states
     * @throws IllegalStateException if the state transition is invalid
     */
    private void updateTaskState(ScheduledTask task, TaskStatus newStatus, String error) {
        TaskStatus currentStatus = task.getStatus();
        if (!TaskTransition.isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition from %s to %s for task %s",
                    currentStatus, newStatus, task.getItemId()));
        }

        task.setStatus(newStatus);
        if (error != null) {
            task.setLastError(error);
        }

        // Clear or update related state fields
        if (newStatus == TaskStatus.COMPLETED || newStatus == TaskStatus.FAILED) {
            task.setLockOwner(null);
            task.setLockDate(null);
            task.setWaitingForTaskType(null);
            task.setCurrentStep(null);
            // Update last execution date for completed/failed tasks
            task.setLastExecutionDate(new Date());
        } else if (newStatus == TaskStatus.CRASHED) {
            // For crashed tasks, preserve state for recovery
            task.setCurrentStep("CRASHED");
            // Keep checkpoint data and lock info for potential resume
            Map<String, Object> details = task.getStatusDetails();
            if (details == null) {
                details = new HashMap<>();
                task.setStatusDetails(details);
            }
            details.put("crashTime", new Date());
            details.put("crashedNode", task.getLockOwner());
        } else if (newStatus == TaskStatus.WAITING) {
            task.setLockOwner(null);
            task.setLockDate(null);
        } else if (newStatus == TaskStatus.RUNNING) {
            // Update status details for running tasks
            Map<String, Object> details = task.getStatusDetails();
            if (details == null) {
                details = new HashMap<>();
                task.setStatusDetails(details);
            }
            details.put("startTime", new Date());
            details.put("executingNode", nodeId);
        }

        saveTask(task);
        LOGGER.debug("Task {} state changed from {} to {}", task.getItemId(), currentStatus, newStatus);
    }

    private static final ConditionType PROPERTY_CONDITION_TYPE = new ConditionType();
    static {
        PROPERTY_CONDITION_TYPE.setItemId("propertyCondition");
        PROPERTY_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        PROPERTY_CONDITION_TYPE.setVersion(1L);
        PROPERTY_CONDITION_TYPE.setConditionEvaluator("propertyConditionEvaluator");
        PROPERTY_CONDITION_TYPE.setQueryBuilder("propertyConditionESQueryBuilder");
    };

    private static final ConditionType BOOLEAN_CONDITION_TYPE = new ConditionType();
    static {
        BOOLEAN_CONDITION_TYPE.setItemId("booleanCondition");
        BOOLEAN_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        BOOLEAN_CONDITION_TYPE.setVersion(1L);
        BOOLEAN_CONDITION_TYPE.setQueryBuilder("booleanConditionESQueryBuilder");
        BOOLEAN_CONDITION_TYPE.setConditionEvaluator("booleanConditionEvaluator");
    };

    private final ScheduledFuture<?> DUMMY_FUTURE = new ScheduledFuture<Object>() {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed o) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    };

    public SchedulerServiceImpl() {
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    // Safe accessor methods
    private PersistenceService getPersistenceService() {
        if (shutdownNow) return null;
        return persistenceServiceTracker != null ? persistenceServiceTracker.getService() : persistenceService;
    }

    private ClusterService getClusterService() {
        if (shutdownNow) return null;
        return clusterServiceTracker != null ? clusterServiceTracker.getService() : clusterService;
    }

    @PostConstruct
    public void postConstruct() {
        if (bundleContext == null) {
            LOGGER.error("BundleContext is null, cannot initialize service trackers");
            return;
        }

        // Initialize service trackers only if we don't have direct service references
        if (persistenceService == null && clusterService == null) {
            initializeServiceTrackers();
        }

        // Initialize managers
        this.stateManager = new TaskStateManager();
        this.lockManager = new TaskLockManager(nodeId, lockTimeout, metricsManager, this);
        this.historyManager = new TaskHistoryManager(nodeId, metricsManager);
        this.validationManager = new TaskValidationManager();

        // Wait for persistence service to be available before continuing
        waitForPersistenceService();

        waitForQueryBuilders();

        this.executionManager = new TaskExecutionManager(nodeId, threadPoolSize, stateManager,
            lockManager, metricsManager, historyManager, getPersistenceService(), this);
        this.recoveryManager = new TaskRecoveryManager(nodeId, getPersistenceService(), stateManager,
            lockManager, metricsManager, executionManager, this);

        if (executorNode) {
            running.set(true);
            // Start task checking thread using the execution manager
            executionManager.startTaskChecker(this::checkTasks);
            // Queue task purge initialization instead of calling directly
            queuePendingOperation(OperationType.INITIALIZE_TASK_PURGE, "Initialize task purge");
        }

        if (nodeId == null) {
            nodeId = UUID.randomUUID().toString();
        }

        LOGGER.info("Scheduler service initialized. Node ID: {}, Executor node: {}, Thread pool size: {}",
            nodeId, executorNode, Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize));

        // Initialize service tracker for TaskExecutors
        initializeTaskExecutorTracker();

        // Mark services as initialized and process any pending operations
        servicesInitialized.set(true);
        servicesInitializedLatch.countDown();
        
        // Process any pending operations that were queued during initialization
        processPendingOperations();
    }

    private void initializeServiceTrackers() {
        /**
         * Initialize service trackers with custom lifecycle management to handle the Aries Blueprint bug.
         * This approach ensures:
         * 1. Services are properly tracked and managed
         * 2. We can handle service unavailability gracefully
         * 3. We maintain explicit control over service lifecycle
         * 4. We can implement proper shutdown order in preDestroy()
         */
        // Persistence service tracker
        persistenceServiceTracker = new ServiceTracker<>(
            bundleContext,
            PersistenceService.class,
            new ServiceTrackerCustomizer<PersistenceService, PersistenceService>() {
                @Override
                public PersistenceService addingService(ServiceReference<PersistenceService> reference) {
                    PersistenceService service = bundleContext.getService(reference);
                    if (service != null) {
                        persistenceService = service;
                        persistenceServiceAvailable.set(true);
                        persistenceServiceLatch.countDown();
                        LOGGER.info("PersistenceService acquired");
                    }
                    return service;
                }

                @Override
                public void modifiedService(ServiceReference<PersistenceService> reference, PersistenceService service) {
                    // No action needed
                }

                @Override
                public void removedService(ServiceReference<PersistenceService> reference, PersistenceService service) {
                    LOGGER.info("PersistenceService removed");
                    persistenceService = null;
                    persistenceServiceAvailable.set(false);
                    bundleContext.ungetService(reference);
                }
            }
        );
        persistenceServiceTracker.open();

        // Cluster service tracker
        clusterServiceTracker = new ServiceTracker<>(
            bundleContext,
            ClusterService.class,
            new ServiceTrackerCustomizer<ClusterService, ClusterService>() {
                @Override
                public ClusterService addingService(ServiceReference<ClusterService> reference) {
                    ClusterService service = bundleContext.getService(reference);
                    if (service != null) {
                        clusterService = service;
                        LOGGER.info("ClusterService acquired");

                        // Handle case where the ClusterService is set after SchedulerService is initialized
                        if (running.get() && service instanceof org.apache.unomi.services.impl.cluster.ClusterServiceImpl) {
                            LOGGER.info("Initializing ClusterService scheduled tasks...");
                            try {
                                ((org.apache.unomi.services.impl.cluster.ClusterServiceImpl) service).initializeScheduledTasks();
                            } catch (Exception e) {
                                LOGGER.error("Error initializing ClusterService scheduled tasks", e);
                            }
                        }
                    }
                    return service;
                }

                @Override
                public void modifiedService(ServiceReference<ClusterService> reference, ClusterService service) {
                    // No action needed
                }

                @Override
                public void removedService(ServiceReference<ClusterService> reference, ClusterService service) {
                    LOGGER.info("ClusterService removed");
                    clusterService = null;
                    bundleContext.ungetService(reference);
                }
            }
        );
        clusterServiceTracker.open();
    }

    private void waitForPersistenceService() {
        // In test environment, we might have a direct reference to persistenceService
        if (persistenceService != null) {
            LOGGER.debug("Using existing PersistenceService reference");
            persistenceServiceAvailable.set(true);
            persistenceServiceLatch.countDown();
            return;
        }

        // Start a background thread to wait for persistence service
        new Thread(() -> {
            try {
                // Wait up to 30 seconds for persistence service to be available
                PersistenceService service = null;
                for (int i = 0; i < 30 && !shutdownNow; i++) {
                    service = getPersistenceService();
                    if (service != null) break;

                    try {
                        LOGGER.info("Waiting for PersistenceService to be available...");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (service != null && !shutdownNow) {
                    persistenceServiceAvailable.set(true);
                    LOGGER.info("PersistenceService is now available");
                } else {
                    LOGGER.warn("PersistenceService not available after waiting. Some features may be unavailable.");
                }
            } finally {
                persistenceServiceLatch.countDown();
            }
        }, "persistence-service-waiter").start();
    }

    private void initializeTaskExecutorTracker() {
        if (bundleContext != null) {
            taskExecutorTracker = new ServiceTracker<>(bundleContext, TaskExecutor.class,
                    new ServiceTrackerCustomizer<TaskExecutor, TaskExecutor>() {
                        @Override
                        public TaskExecutor addingService(ServiceReference<TaskExecutor> reference) {
                            TaskExecutor executor = bundleContext.getService(reference);
                            if (executor != null) {
                                registerTaskExecutor(executor);
                                LOGGER.info("Registered TaskExecutor for type: {}", executor.getTaskType());
                            }
                            return executor;
                        }

                        @Override
                        public void modifiedService(ServiceReference<TaskExecutor> reference, TaskExecutor service) {
                            // Re-register in case task type changed
                            unregisterTaskExecutor(service);
                            registerTaskExecutor(service);
                            LOGGER.info("Updated TaskExecutor for type: {}", service.getTaskType());
                        }

                        @Override
                        public void removedService(ServiceReference<TaskExecutor> reference, TaskExecutor service) {
                            unregisterTaskExecutor(service);
                            bundleContext.ungetService(reference);
                            LOGGER.info("Unregistered TaskExecutor for type: {}", service.getTaskType());
                        }
                    });
            taskExecutorTracker.open();
        } else {
            LOGGER.warn("No bundle context provided, cannot initialize task executor service tracker");
        }
    }

    @PreDestroy
    public void preDestroy() {
        /**
         * Explicit shutdown sequence to handle the Aries Blueprint bug.
         * We ensure services are shut down in the correct order:
         * 1. Set shutdown flag first to prevent new operations
         * 2. Clear pending operations queue
         * 3. Release task locks and cancel tasks
         * 4. Shutdown execution manager
         * 5. Release manager references
         * 6. Clear task collections
         * 7. Close service trackers in reverse order of dependency
         *
         * This explicit shutdown sequence prevents the deadlocks and timeout issues
         * that occur with Blueprint's default shutdown behavior.
         */
        shutdownNow = true; // Set shutdown flag before other operations
        running.set(false);

        LOGGER.info("SchedulerService preDestroy: beginning shutdown process");

        // Clear pending operations queue
        int pendingCount = pendingOperations.size();
        if (pendingCount > 0) {
            pendingOperations.clear();
            LOGGER.info("Cleared {} pending operations during shutdown", pendingCount);
        }

        // Notify all managers about shutdown
        if (recoveryManager != null) {
            try {
                recoveryManager.prepareForShutdown();
                LOGGER.debug("Recovery manager prepared for shutdown");
            } catch (Exception e) {
                LOGGER.debug("Error preparing recovery manager for shutdown: {}", e.getMessage());
            }
        }

        // Release any task locks owned by this node - use local reference to avoid service lookup
        PersistenceService persistenceServiceLocal = this.persistenceService;
        if (persistenceServiceLocal != null && !shutdownNow) {
            try {
                List<ScheduledTask> tasks = findTasksByLockOwner(nodeId);
                for (ScheduledTask task : tasks) {
                    try {
                        lockManager.releaseLock(task);
                    } catch (Exception e) {
                        LOGGER.debug("Error releasing lock for task {} during shutdown: {}", task.getItemId(), e.getMessage());
                    }
                }
                LOGGER.debug("Task locks released");
            } catch (Exception e) {
                LOGGER.warn("Error finding locked tasks during shutdown: {}", e.getMessage());
            }
        }

        if (taskPurgeTask != null) {
            try {
                cancelTask(taskPurgeTask.getItemId());
                LOGGER.debug("Task purge cancelled");
            } catch (Exception e) {
                LOGGER.debug("Error cancelling purge task during shutdown: {}", e.getMessage());
            }
        }

        // Shutdown execution manager
        try {
            if (executionManager != null) {
                executionManager.shutdown();
                LOGGER.debug("Execution manager shutdown completed");
            }
        } catch (Exception e) {
            LOGGER.debug("Error shutting down execution manager: {}", e.getMessage());
        }

        // Release all manager references
        this.recoveryManager = null;
        this.executionManager = null;
        this.lockManager = null;
        this.stateManager = null;
        this.historyManager = null;
        this.validationManager = null;

        // Clear task collections
        try {
            this.metricsManager.resetMetrics();
            this.taskExecutors.clear();
            this.nonPersistentTasks.clear();
            this.waitingNonPersistentTasks.clear();
            LOGGER.debug("Task collections cleared");
        } catch (Exception e) {
            LOGGER.debug("Error clearing task collections: {}", e.getMessage());
        }

        // Close trackers in reverse order of dependency
        if (taskExecutorTracker != null) {
            try {
                taskExecutorTracker.close();
                LOGGER.debug("Task executor tracker closed");
            } catch (Exception e) {
                LOGGER.debug("Error closing task executor tracker: {}", e.getMessage());
            }
            taskExecutorTracker = null;
        }

        // Close service trackers
        if (clusterServiceTracker != null) {
            try {
                clusterServiceTracker.close();
                clusterService = null;
                LOGGER.debug("Cluster service tracker closed");
            } catch (Exception e) {
                LOGGER.debug("Error closing cluster service tracker: {}", e.getMessage());
            }
            clusterServiceTracker = null;
        }

        if (persistenceServiceTracker != null) {
            try {
                persistenceServiceTracker.close();
                persistenceService = null;
                LOGGER.debug("Persistence service tracker closed");
            } catch (Exception e) {
                LOGGER.debug("Error closing persistence service tracker: {}", e.getMessage());
            }
            persistenceServiceTracker = null;
        }

        LOGGER.info("SchedulerService shutdown completed");
    }

    void checkTasks() {
        if (shutdownNow || !running.get() || checkTasksRunning.get() || !executorNode) {
            return;
        }

        if (!checkTasksRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            // Skip task processing during shutdown
            if (shutdownNow) {
                return;
            }

            // Wait for persistence service if not available
            if (!persistenceServiceAvailable.get()) {
                try {
                    LOGGER.debug("Waiting for persistence service to be available...");
                    if (!persistenceServiceLatch.await(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("Timeout waiting for persistence service. Skipping task check.");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Wait for query builders if not available
            if (!queryBuildersAvailable.get()) {
                try {
                    LOGGER.debug("Waiting for query builders to be available...");
                    if (!queryBuildersLatch.await(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("Timeout waiting for query builders. Skipping task check.");
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // Check for crashed tasks first
            recoveryManager.recoverCrashedTasks();

            // Get all enabled tasks that are either scheduled or waiting
            List<ScheduledTask> tasks = findEnabledScheduledOrWaitingTasks();
            if (tasks == null) {
                LOGGER.debug("No tasks found or persistence service unavailable");
                return;
            }

            // Also check in-memory tasks
            List<ScheduledTask> inMemoryTasks = nonPersistentTasks.values().stream()
                .filter(task -> task.isEnabled() &&
                    (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED ||
                     task.getStatus() == ScheduledTask.TaskStatus.WAITING))
                .collect(Collectors.toList());

            // Add in-memory tasks to the list of tasks to check
            if (!inMemoryTasks.isEmpty() && tasks != null) {
                LOGGER.debug("Node {} found {} in-memory tasks to check", nodeId, inMemoryTasks.size());
                tasks.addAll(inMemoryTasks);
            }

            if (tasks == null || tasks.isEmpty()) {
                return;
            }

            LOGGER.debug("Node {} found {} total tasks to check", nodeId, tasks.size());

            // Sort and group tasks
            sortTasksByPriority(tasks);
            Map<String, List<ScheduledTask>> tasksByType = groupTasksByType(tasks);

            // Process each task type
            for (Map.Entry<String, List<ScheduledTask>> entry : tasksByType.entrySet()) {
                if (shutdownNow) return;
                processTaskGroup(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            LOGGER.error("Error checking tasks", e);
        } finally {
            checkTasksRunning.set(false);
        }
    }

    private List<ScheduledTask> findTasksByLockOwner(String owner) {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            return new ArrayList<>();
        }

        try {
            Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "lockOwner");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", owner);
            return service.query(condition, null, ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Error finding tasks by lock owner: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ScheduledTask> findEnabledScheduledOrWaitingTasks() {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            return new ArrayList<>();
        }

        try {
            Condition enabledCondition = new Condition(PROPERTY_CONDITION_TYPE);
            enabledCondition.setParameter("propertyName", "enabled");
            enabledCondition.setParameter("comparisonOperator", "equals");
            enabledCondition.setParameter("propertyValue", "true");

            Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "in");
            statusCondition.setParameter("propertyValues", Arrays.asList(
                ScheduledTask.TaskStatus.SCHEDULED,
                ScheduledTask.TaskStatus.WAITING
            ));

            Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(enabledCondition, statusCondition));

            return service.query(andCondition, "creationDate:asc", ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Error finding enabled scheduled or waiting tasks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void sortTasksByPriority(List<ScheduledTask> tasks) {
        tasks.sort((t1, t2) -> {
            // First by status (WAITING before SCHEDULED)
            int statusCompare = Boolean.compare(
                t1.getStatus() == ScheduledTask.TaskStatus.WAITING,
                t2.getStatus() == ScheduledTask.TaskStatus.WAITING
            );
            if (statusCompare != 0) return -statusCompare;

            // Then by creation date
            int dateCompare = t1.getCreationDate().compareTo(t2.getCreationDate());
            if (dateCompare != 0) return dateCompare;

            // Finally by next execution date
            Date next1 = t1.getNextScheduledExecution();
            Date next2 = t2.getNextScheduledExecution();
            if (next1 == null) return next2 == null ? 0 : -1;
            if (next2 == null) return 1;
            return next1.compareTo(next2);
        });
    }

    private Map<String, List<ScheduledTask>> groupTasksByType(List<ScheduledTask> tasks) {
        Map<String, List<ScheduledTask>> tasksByType = new HashMap<>();
        for (ScheduledTask task : tasks) {
            tasksByType.computeIfAbsent(task.getTaskType(), k -> new ArrayList<>()).add(task);
        }
        return tasksByType;
    }

    private void processTaskGroup(String taskType, List<ScheduledTask> tasks) {
        TaskExecutor executor = executionManager.getTaskExecutor(taskType);
        if (executor == null) {
            return;
        }

        // Check if any task of this type is running with a valid lock
        boolean hasRunningTask = hasRunningTaskOfType(taskType);
        if (!hasRunningTask) {
            // Get the first task that should execute
            for (ScheduledTask task : tasks) {
                if (shouldExecuteTask(task)) {
                    // All tasks here are persistent since they come from persistence service query
                    executionManager.executeTask(task, executor);
                    break;
                }
            }
        }
    }

    /**
     * Schedules a task for execution based on its configuration
     */
    private void scheduleTaskExecution(ScheduledTask task, TaskExecutor executor) {
        if (!task.isEnabled()) {
            LOGGER.debug("Task {} is disabled, skipping scheduling", task.getItemId());
            return;
        }

        // Don't schedule tasks that are already running
        if (task.getStatus() == TaskStatus.RUNNING) {
            LOGGER.debug("Task {} is already running, skipping scheduling", task.getItemId());
            return;
        }

        // Create task wrapper that will execute the task
        Runnable taskWrapper = () -> executionManager.executeTask(task, executor);

        if (!task.isPersistent()) {
            // For in-memory tasks, schedule directly with the execution manager
            executionManager.scheduleTask(task, taskWrapper);
        } else {
            // For persistent tasks, calculate next execution time and update state
            stateManager.calculateNextExecutionTime(task);
            if (task.getStatus() != TaskStatus.SCHEDULED) {
                stateManager.updateTaskState(task, TaskStatus.SCHEDULED, null, nodeId);
            }
            updateTaskInPersistence(task);

            // If task is ready to execute now, execute it
            if (isTaskDueForExecution(task)) {
                executionManager.executeTask(task, executor);
            }
        }
    }

    private boolean hasRunningTaskOfType(String taskType) {
        List<ScheduledTask> runningTasks = findTasksByTypeAndStatus(taskType, ScheduledTask.TaskStatus.RUNNING);
        return runningTasks.stream().anyMatch(task -> !lockManager.isLockExpired(task));
    }

    private List<ScheduledTask> findTasksByTypeAndStatus(String taskType, ScheduledTask.TaskStatus status) {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            return new ArrayList<>();
        }

        try {
            Condition typeCondition = new Condition(PROPERTY_CONDITION_TYPE);
            typeCondition.setParameter("propertyName", "taskType");
            typeCondition.setParameter("comparisonOperator", "equals");
            typeCondition.setParameter("propertyValue", taskType);

            Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", status.toString());

            Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(typeCondition, statusCondition));

            return service.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Error finding tasks by type and status: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean shouldExecuteTask(ScheduledTask task) {
        try {
            validationManager.validateExecutionPrerequisites(task, nodeId);
        } catch (IllegalStateException e) {
            LOGGER.debug("Task {} not ready for execution: {}", task.getItemId(), e.getMessage());
            return false;
        }

        // Check if task should run on this node
        if (!task.isRunOnAllNodes() && !executorNode) {
            return false;
        }

        // Check task dependencies
        if (task.getDependsOn() != null && !task.getDependsOn().isEmpty()) {
            Map<String, ScheduledTask> dependencies = new HashMap<>();
            for (String dependencyId : task.getDependsOn()) {
                ScheduledTask dependency = getTask(dependencyId);
                if (dependency != null) {
                    dependencies.put(dependencyId, dependency);
                }
            }
            if (!stateManager.canRescheduleTask(task, dependencies)) {
                return false;
            }
        }

        // For waiting tasks, they are already ordered by creation date
        if (task.getStatus() == ScheduledTask.TaskStatus.WAITING) {
            return true;
        }

        // For scheduled tasks, check execution timing
        if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED) {
            return isTaskDueForExecution(task);
        }

        return false;
    }

    private boolean isTaskDueForExecution(ScheduledTask task) {
        // For one-shot tasks or initial execution
        if (task.getLastExecutionDate() == null) {
            if (task.getInitialDelay() > 0) {
                // Check if initial delay has passed
                long startTime = task.getCreationDate().getTime() +
                    task.getTimeUnit().toMillis(task.getInitialDelay());
                return System.currentTimeMillis() >= startTime;
            }
            return true; // Execute immediately if no initial delay
        }

        // For periodic tasks, check next scheduled execution
        if (!task.isOneShot() && task.getPeriod() > 0) {
            Date nextExecution = task.getNextScheduledExecution();
            return nextExecution != null &&
                   System.currentTimeMillis() >= nextExecution.getTime();
        }

        return false;
    }

    @Override
    public void scheduleTask(ScheduledTask task) {
        if (areServicesReady()) {
            scheduleTaskInternal(task);
        } else {
            queuePendingOperation(OperationType.SCHEDULE_TASK, 
                "Schedule task: " + task.getItemId(), task);
        }
    }

    /**
     * Internal method to schedule a task - called when services are ready
     * @param task The task to schedule
     */
    private void scheduleTaskInternal(ScheduledTask task) {
        if (!task.isEnabled()) {
            return;
        }

        Map<String, ScheduledTask> existingTasks = new HashMap<>();
        if (task.getDependsOn() != null) {
            for (String dependencyId : task.getDependsOn()) {
                ScheduledTask dependency = getTask(dependencyId);
                if (dependency != null) {
                    existingTasks.put(dependencyId, dependency);
                }
            }
        }

        validationManager.validateTask(task, existingTasks);

        // Store task
        if (!saveTask(task)) {
            LOGGER.error("Failed to save task: {}", task.getItemId());
            return;
        }

        // Get executor and schedule task
        TaskExecutor executor = executionManager.getTaskExecutor(task.getTaskType());
        if (executor != null && (task.isRunOnAllNodes() || executorNode)) {
            scheduleTaskExecution(task, executor);
        }
    }

    @Override
    public void cancelTask(String taskId) {
        if (areServicesReady()) {
            cancelTaskInternal(taskId);
        } else {
            queuePendingOperation(OperationType.CANCEL_TASK, 
                "Cancel task: " + taskId, taskId);
        }
    }

    /**
     * Internal method to cancel a task - called when services are ready
     * @param taskId The task ID to cancel
     */
    private void cancelTaskInternal(String taskId) {
        if (shutdownNow) {
            return;
        }
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            // Only cancel if in a cancellable state
            if (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED ||
                task.getStatus() == ScheduledTask.TaskStatus.WAITING ||
                task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {

                task.setEnabled(false);
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.CANCELLED, null, nodeId);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CANCELLED);
                historyManager.recordCancellation(task);

                executionManager.cancelTask(taskId);
                lockManager.releaseLock(task);

                if (!saveTask(task)) {
                    LOGGER.error("Failed to save cancelled task state: {}", taskId);
                }
            }
        }
    }

    @Override
    public ScheduledTask createTask(String taskType, Map<String, Object> parameters,
                                  long initialDelay, long period, TimeUnit timeUnit,
                                  boolean fixedRate, boolean oneShot, boolean allowParallelExecution,
                                  boolean persistent) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(UUID.randomUUID().toString());
        task.setTaskType(taskType);
        task.setParameters(parameters != null ? parameters : Collections.emptyMap());
        task.setInitialDelay(initialDelay);
        task.setPeriod(period);
        task.setTimeUnit(timeUnit);
        task.setFixedRate(fixedRate);
        task.setOneShot(oneShot);
        task.setAllowParallelExecution(allowParallelExecution);
        task.setEnabled(true);
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        task.setPersistent(persistent);
        task.setCreationDate(new Date());

        Map<String, Object> details = new HashMap<>();
        details.put("executionHistory", new ArrayList<>());
        task.setStatusDetails(details);

        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_CREATED);
        return task;
    }

    @Override
    public List<ScheduledTask> getAllTasks() {
        List<ScheduledTask> allTasks = new ArrayList<>(getPersistentTasks());
        allTasks.addAll(getMemoryTasks());
        return allTasks;
    }

    @Override
    public ScheduledTask getTask(String taskId) {
        if (shutdownNow) {
            return null;
        }

        // First check in-memory tasks which is faster
        ScheduledTask memoryTask = nonPersistentTasks.get(taskId);
        if (memoryTask != null) {
            return memoryTask;
        }

        // Then check persistent tasks
        PersistenceService service = getPersistenceService();
        if (service == null) {
            return null;
        }

        try {
            return service.load(taskId, ScheduledTask.class);
        } catch (Exception e) {
            LOGGER.error("Error loading task {}: {}", taskId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ScheduledTask> getPersistentTasks() {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow) {
            return new ArrayList<>();
        }

        try {
            return service.getAllItems(ScheduledTask.class, 0, -1, null).getList();
        } catch (Exception e) {
            LOGGER.error("Error getting persistent tasks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<ScheduledTask> getMemoryTasks() {
        return new ArrayList<>(nonPersistentTasks.values());
    }

    @Override
    public void registerTaskExecutor(TaskExecutor executor) {
        if (areServicesReady()) {
            executionManager.registerTaskExecutor(executor);
        } else {
            queuePendingOperation(OperationType.REGISTER_TASK_EXECUTOR, 
                "Register task executor: " + executor.getTaskType(), executor);
        }
    }

    @Override
    public void unregisterTaskExecutor(TaskExecutor executor) {
        if (areServicesReady()) {
            executionManager.unregisterTaskExecutor(executor);
        } else {
            queuePendingOperation(OperationType.UNREGISTER_TASK_EXECUTOR, 
                "Unregister task executor: " + executor.getTaskType(), executor);
        }
    }

    @Override
    public boolean isExecutorNode() {
        return executorNode;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public void setExecutorNode(boolean executorNode) {
        this.executorNode = executorNode;
    }

    public void setLockTimeout(long lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    public void setCompletedTaskTtlDays(long completedTaskTtlDays) {
        this.completedTaskTtlDays = completedTaskTtlDays;
    }

    public void setPurgeTaskEnabled(boolean purgeTaskEnabled) {
        this.purgeTaskEnabled = purgeTaskEnabled;
    }

    public static long getTimeDiffInSeconds(int hourInUtc, ZonedDateTime now) {
        ZonedDateTime nextRun = now.withHour(hourInUtc).withMinute(0).withSecond(0);
        if(now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }

    @Override
    public void recoverCrashedTasks() {
        if (areServicesReady()) {
            if (executorNode) {
                recoveryManager.recoverCrashedTasks();
            }
        } else {
            queuePendingOperation(OperationType.RECOVER_CRASHED_TASKS, "Recover crashed tasks");
        }
    }

    private void waitForQueryBuilders() {
        try {
            // Start a background thread to wait for query builders
            new Thread(() -> {
                try {
                    // Wait for query builders to be available before starting recovery
                    if (queryBuilderAvailabilityTracker != null) {
                        LOGGER.info("Waiting for query builders to be available...");
                        if (queryBuilderAvailabilityTracker.waitForQueryBuilders(120000)) { // 120 second timeout
                            queryBuildersAvailable.set(true);
                            LOGGER.info("All required query builders are now available.");
                        } else {
                            LOGGER.warn("Timeout waiting for query builders. Some tasks may not be recoverable.");
                        }
                    } else {
                        // If no tracker is available, consider query builders as available
                        queryBuildersAvailable.set(true);
                    }
                } catch (InterruptedException e) {
                    LOGGER.warn("Interrupted while waiting for query builders", e);
                    Thread.currentThread().interrupt();
                } finally {
                    queryBuildersLatch.countDown();
                }
            }, "query-builders-waiter").start();
        } catch (Exception e) {
            LOGGER.warn("Error starting query builders waiter thread", e);
            queryBuildersLatch.countDown();
        }
    }

    @Override
    public void retryTask(String taskId, boolean resetFailureCount) {
        if (areServicesReady()) {
            retryTaskInternal(taskId, resetFailureCount);
        } else {
            queuePendingOperation(OperationType.RETRY_TASK, 
                "Retry task: " + taskId + " (reset: " + resetFailureCount + ")", taskId, resetFailureCount);
        }
    }

    /**
     * Internal method to retry a task - called when services are ready
     * @param taskId The task ID to retry
     * @param resetFailureCount Whether to reset the failure count
     */
    private void retryTaskInternal(String taskId, boolean resetFailureCount) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.FAILED) {
            if (resetFailureCount) {
                task.setFailureCount(0);
            }
            task.setLastExecutionDate(null); // we have to do this to force the task to execute again
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RETRIED);
            scheduleTaskInternal(task);
        }
    }

    @Override
    public void resumeTask(String taskId) {
        if (areServicesReady()) {
            resumeTaskInternal(taskId);
        } else {
            queuePendingOperation(OperationType.RESUME_TASK, 
                "Resume task: " + taskId, taskId);
        }
    }

    /**
     * Internal method to resume a task - called when services are ready
     * @param taskId The task ID to resume
     */
    private void resumeTaskInternal(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            TaskExecutor executor = executionManager.getTaskExecutor(task.getTaskType());
            if (executor != null && executor.canResume(task)) {
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
                scheduleTaskInternal(task);
            }
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy) {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        try {
            Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "status");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", status.toString());
            return service.query(condition, sortBy, ScheduledTask.class, offset, size);
        } catch (Exception e) {
            LOGGER.error("Error getting tasks by status: {}", e.getMessage());
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy) {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }

        try {
            Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "taskType");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", taskType);
            return service.query(condition, sortBy, ScheduledTask.class, offset, size);
        } catch (Exception e) {
            LOGGER.error("Error getting tasks by type: {}", e.getMessage());
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }
    }

    private void initializeTaskPurge() {
        if (areServicesReady()) {
            initializeTaskPurgeInternal();
        } else {
            queuePendingOperation(OperationType.INITIALIZE_TASK_PURGE, "Initialize task purge");
        }
    }

    /**
     * Internal method to initialize task purge - called when services are ready
     */
    private void initializeTaskPurgeInternal() {
        if (!purgeTaskEnabled) {
            return;
        }

        // Register the task executor for task purge
        TaskExecutor taskPurgeExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "task-purge";
            }

            @Override
            public void execute(ScheduledTask task, TaskExecutor.TaskStatusCallback callback) {
                try {
                    purgeOldTasks();
                    callback.complete();
                } catch (Throwable t) {
                    LOGGER.error("Error while purging old tasks", t);
                    callback.fail(t.getMessage());
                }
            }
        };

        registerTaskExecutor(taskPurgeExecutor);

        // Check if a task purge task already exists
        List<ScheduledTask> existingTasks = getTasksByType("task-purge", 0, 1, null).getList();
        ScheduledTask taskPurgeTask = null;

        if (!existingTasks.isEmpty() && existingTasks.get(0).isSystemTask()) {
            // Reuse the existing task if it's a system task
            taskPurgeTask = existingTasks.get(0);
            // Update task configuration if needed
            taskPurgeTask.setPeriod(1);
            taskPurgeTask.setTimeUnit(TimeUnit.DAYS);
            taskPurgeTask.setFixedRate(true);
            taskPurgeTask.setEnabled(true);
            saveTask(taskPurgeTask);
            LOGGER.info("Reusing existing system task purge task: {}", taskPurgeTask.getItemId());
        } else {
            // Create a new task if none exists or existing one isn't a system task
            taskPurgeTask = newTask("task-purge")
                .withPeriod(1, TimeUnit.DAYS)
                .withFixedRate()
                .asSystemTask()
                .schedule();
            LOGGER.info("Created new system task purge task: {}", taskPurgeTask.getItemId());
        }
    }

    void purgeOldTasks() {
        if (!executorNode || shutdownNow) {
            return;
        }

        PersistenceService service = getPersistenceService();
        if (service == null || !queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            LOGGER.warn("Cannot purge old tasks - persistence service unavailable");
            return;
        }

        try {
            LOGGER.debug("Starting purge of old completed tasks");
            long purgeBeforeTime = System.currentTimeMillis() - (completedTaskTtlDays * 24 * 60 * 60 * 1000);
            Date purgeBeforeDate = new Date(purgeBeforeTime);

            Condition statusCondition = new Condition(PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", ScheduledTask.TaskStatus.COMPLETED.toString());

            Condition dateCondition = new Condition(PROPERTY_CONDITION_TYPE);
            dateCondition.setParameter("propertyName", "lastExecutionDate");
            dateCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            dateCondition.setParameter("propertyValueDate", purgeBeforeDate);

            Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(statusCondition, dateCondition));

            service.removeByQuery(andCondition, ScheduledTask.class);
            LOGGER.info("Completed purge of old tasks before date: {}", purgeBeforeDate);
        } catch (Exception e) {
            LOGGER.error("Error purging old tasks", e);
        }
    }

    /**
     * Builder class to simplify task creation with fluent API
     */
    public TaskBuilder newTask(String taskType) {
        return new TaskBuilder(this, taskType);
    }

    private boolean updateTaskInPersistence(ScheduledTask task) {
        return saveTask(task);
    }

    /**
     * Saves a task to the persistence service if it's persistent.
     * @param task The task to save
     * @return true if the task was successfully saved, false otherwise
     */
    @Override
    public boolean saveTask(ScheduledTask task) {
        if (task == null || shutdownNow) {
            return false;
        }

        if (task.isPersistent()) {
            PersistenceService service = getPersistenceService();
            if (service == null) {
                LOGGER.warn("Cannot save task {} - persistence service unavailable", task.getItemId());
                return false;
            }

            try {
                service.save(task);
                LOGGER.debug("Saved task {} to persistence", task.getItemId());
                return true;
            } catch (Exception e) {
                LOGGER.error("Error saving task {} to persistence", task.getItemId(), e);
                return false;
            }
        } else {
            LOGGER.debug("Saving task {} in memory", task.getItemId());
            nonPersistentTasks.put(task.getItemId(), task);
            return true;
        }
    }

    @Override
    public ScheduledTask createRecurringTask(String taskType, long period, TimeUnit timeUnit, Runnable runnable, boolean persistent) {
        return newTask(taskType)
            .withPeriod(period, timeUnit)
            .withFixedRate()
            .withSimpleExecutor(runnable)
            .nonPersistent()
            .schedule();
    }

    @Override
    public long getMetric(String metric) {
        return metricsManager.getMetric(metric);
    }

    @Override
    public void resetMetrics() {
        metricsManager.resetMetrics();
    }

    @Override
    public Map<String, Long> getAllMetrics() {
        Map<String, Long> metrics = metricsManager.getAllMetrics();
        // Add pending operations count to metrics
        metrics.put("pendingOperations", (long) pendingOperations.size());
        return metrics;
    }

    /**
     * Gets the number of pending operations waiting to be processed
     * @return The number of pending operations
     */
    public int getPendingOperationsCount() {
        return pendingOperations.size();
    }

    /**
     * Gets a list of pending operations for debugging purposes
     * @return List of pending operation descriptions
     */
    public List<String> getPendingOperationsList() {
        return pendingOperations.stream()
            .map(PendingOperation::getDescription)
            .collect(Collectors.toList());
    }

    /**
     * Waits for all required services to be ready
     * @param timeout Maximum time to wait in milliseconds
     * @return true if services are ready within timeout, false otherwise
     * @throws InterruptedException if the wait is interrupted
     */
    public boolean waitForServicesReady(long timeout) throws InterruptedException {
        if (areServicesReady()) {
            return true;
        }

        long startTime = System.currentTimeMillis();
        long remainingTime = timeout;

        while (remainingTime > 0 && !areServicesReady()) {
            // Wait for services initialized latch
            if (!servicesInitializedLatch.await(Math.min(remainingTime, 1000), TimeUnit.MILLISECONDS)) {
                remainingTime = timeout - (System.currentTimeMillis() - startTime);
                continue;
            }

            // Wait for persistence service
            if (!persistenceServiceAvailable.get()) {
                if (!persistenceServiceLatch.await(Math.min(remainingTime, 1000), TimeUnit.MILLISECONDS)) {
                    remainingTime = timeout - (System.currentTimeMillis() - startTime);
                    continue;
                }
            }

            // Wait for query builders
            if (!queryBuildersAvailable.get()) {
                if (!queryBuildersLatch.await(Math.min(remainingTime, 1000), TimeUnit.MILLISECONDS)) {
                    remainingTime = timeout - (System.currentTimeMillis() - startTime);
                    continue;
                }
            }

            remainingTime = timeout - (System.currentTimeMillis() - startTime);
        }

        return areServicesReady();
    }

    /**
     * Refreshes the task indices to ensure up-to-date view.
     * This is used by the distributed locking mechanism to ensure
     * all nodes see the latest task state.
     */
    public void refreshTasks() {
        PersistenceService service = getPersistenceService();
        if (service == null || shutdownNow) {
            return;
        }

        try {
            service.refreshIndex(ScheduledTask.class);
        } catch (Exception e) {
            LOGGER.error("Error refreshing task indices", e);
        }
    }

    /**
     * Saves a task with immediate refresh to ensure changes are visible.
     * This is used by the distributed locking mechanism to ensure lock
     * information is immediately visible to all nodes.
     *
     * @param task The task to save
     * @return true if the operation was successful
     */
    public boolean saveTaskWithRefresh(ScheduledTask task) {
        if (task == null || shutdownNow) {
            return false;
        }

        if (task.isPersistent()) {
            PersistenceService service = getPersistenceService();
            if (service == null) {
                LOGGER.warn("Cannot save task with refresh - persistence service unavailable");
                return false;
            }

            try {
                // Save with optimistic concurrency control
                // Refresh is now handled automatically by the refresh policy
                return service.save(task);
            } catch (Exception e) {
                LOGGER.error("Error saving task {}", task.getItemId(), e);
                return false;
            }
        } else {
            // For non-persistent tasks, just save normally
            return saveTask(task);
        }
    }

    /**
     * Returns the list of currently active cluster nodes.
     * This is used for node affinity in the distributed locking mechanism.
     *
     * This method is designed to handle the case when ClusterService is not available (null),
     * which can happen during startup when services are being initialized in a particular order,
     * or in standalone mode. When ClusterService is null, this method will return just the current
     * node, effectively making this a single-node operation.
     *
     * @return List of active node IDs
     */
    public List<String> getActiveNodes() {
        Set<String> activeNodes = new HashSet<>();

        // Add this node
        activeNodes.add(nodeId);

        // Use ClusterService if available to get cluster nodes
        ClusterService clusterServiceLocal = getClusterService();
        if (clusterServiceLocal != null && !shutdownNow) {
            try {
                List<ClusterNode> clusterNodes = clusterServiceLocal.getClusterNodes();
                if (clusterNodes != null && !clusterNodes.isEmpty()) {
                    // Consider nodes with recent heartbeats as active
                    long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes threshold

                    for (ClusterNode node : clusterNodes) {
                        if (node.getLastHeartbeat() > cutoffTime) {
                            activeNodes.add(node.getItemId());
                        }
                    }

                    LOGGER.debug("Detected active cluster nodes via ClusterService: {}", activeNodes);
                    return new ArrayList<>(activeNodes);
                }
            } catch (Exception e) {
                LOGGER.warn("Error retrieving cluster nodes from ClusterService: {}", e.getMessage());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Error details:", e);
                }
            }
        }

        // Fallback: Look for other active nodes by checking tasks with recent locks
        PersistenceService persistenceServiceLocal = getPersistenceService();
        if (persistenceServiceLocal != null && !shutdownNow && queryBuilderAvailabilityTracker.areAllQueryBuildersAvailable()) {
            try {
                // Create a condition to find tasks with recent locks
                Condition recentLocksCondition = new Condition();
                recentLocksCondition.setConditionType(PROPERTY_CONDITION_TYPE);
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("propertyName", "lockDate");
                parameters.put("comparisonOperator", "exists");
                recentLocksCondition.setParameterValues(parameters);

                // Query for tasks with lock information
                List<ScheduledTask> recentlyLockedTasks = persistenceServiceLocal.query(recentLocksCondition, "lockDate", ScheduledTask.class);

                // Get current time for filtering
                long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);

                // Extract unique node IDs from lock owners with recent locks
                for (ScheduledTask task : recentlyLockedTasks) {
                    if (task.getLockOwner() != null && task.getLockDate() != null &&
                        task.getLockDate().getTime() > fiveMinutesAgo) {
                        activeNodes.add(task.getLockOwner());
                    }
                }
            } catch (Exception e) {
                // If we can't determine active nodes, just fall back to this node only
                LOGGER.warn("Error detecting active cluster nodes: {}", e.getMessage());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Error details:", e);
                }
            }
        }

        LOGGER.debug("Detected active cluster nodes: {}", activeNodes);
        return new ArrayList<>(activeNodes);
    }

    /**
     * Simulates a crash of the scheduler service by abruptly stopping all operations.
     * This is used for testing crash recovery scenarios.
     */
    public void simulateCrash() {
        shutdownNow = true;
        running.set(false);

        // Release any locks owned by this node
        if (persistenceService != null) {
            try {
                List<ScheduledTask> tasks = findTasksByLockOwner(nodeId);
                for (ScheduledTask task : tasks) {
                    try {
                        lockManager.releaseLock(task);
                    } catch (Exception e) {
                        LOGGER.debug("Error releasing lock for task {} during crash simulation: {}", task.getItemId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error finding locked tasks during crash simulation: {}", e.getMessage());
            }
        }

        // Stop execution manager
        if (executionManager != null) {
            try {
                executionManager.shutdown();
            } catch (Exception e) {
                LOGGER.debug("Error shutting down execution manager during crash simulation: {}", e.getMessage());
            }
        }
    }

    /**
     * For testing purposes only - sets up a mock persistence service
     * This method should be called before postConstruct() in test scenarios
     */
    public void setPersistenceService(PersistenceService service) {
        if (persistenceServiceTracker != null) {
            persistenceServiceTracker.close();
        }
        this.persistenceService = service;
        LOGGER.info("PersistenceService set for testing");
    }

    /**
     * For testing purposes only - sets up a mock cluster service
     * This method should be called before postConstruct() in test scenarios
     */
    public void setClusterService(ClusterService service) {
        if (clusterServiceTracker != null) {
            clusterServiceTracker.close();
        }
        this.clusterService = service;
        LOGGER.info("ClusterService set for testing");
    }

    public void setQueryBuilderAvailabilityTracker(QueryBuilderAvailabilityTracker queryBuilderAvailabilityTracker) {
        this.queryBuilderAvailabilityTracker = queryBuilderAvailabilityTracker;
    }

    public void unsetQueryBuilderAvailabilityTracker(QueryBuilderAvailabilityTracker queryBuilderAvailabilityTracker) {
        this.queryBuilderAvailabilityTracker = null;
    }

    public static class TaskBuilder implements SchedulerService.TaskBuilder {
        private final SchedulerServiceImpl schedulerService;
        private final String taskType;
        private Map<String, Object> parameters = Collections.emptyMap();
        private long initialDelay = 0;
        private long period = 0;
        private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        private boolean fixedRate = true;
        private boolean oneShot = false;
        private boolean allowParallelExecution = true;
        private TaskExecutor executor;
        private boolean persistent = true;
        private boolean runOnAllNodes = false;
        private int maxRetries = 3;  // Default value from ScheduledTask
        private long retryDelay = 60000;  // Default value from ScheduledTask (1 minute)
        private Set<String> dependsOn = new HashSet<>();
        private boolean systemTask = false;

        private TaskBuilder(SchedulerServiceImpl schedulerService, String taskType) {
            this.schedulerService = schedulerService;
            this.taskType = taskType;
        }

        @Override
        public TaskBuilder withParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        @Override
        public TaskBuilder withInitialDelay(long initialDelay, TimeUnit timeUnit) {
            this.initialDelay = initialDelay;
            this.timeUnit = timeUnit;
            return this;
        }

        @Override
        public TaskBuilder withPeriod(long period, TimeUnit timeUnit) {
            this.period = period;
            this.timeUnit = timeUnit;
            return this;
        }

        @Override
        public TaskBuilder withFixedDelay() {
            this.fixedRate = false;
            return this;
        }

        @Override
        public TaskBuilder withFixedRate() {
            this.fixedRate = true;
            return this;
        }

        @Override
        public TaskBuilder asOneShot() {
            this.oneShot = true;
            return this;
        }

        @Override
        public TaskBuilder disallowParallelExecution() {
            this.allowParallelExecution = false;
            return this;
        }

        @Override
        public TaskBuilder withExecutor(TaskExecutor executor) {
            this.executor = executor;
            return this;
        }

        @Override
        public TaskBuilder withSimpleExecutor(Runnable runnable) {
            this.executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return taskType;
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    try {
                        runnable.run();
                        callback.complete();
                    } catch (Exception e) {
                        callback.fail(e.getMessage());
                    }
                }
            };
            return this;
        }

        @Override
        public TaskBuilder nonPersistent() {
            this.persistent = false;
            return this;
        }

        @Override
        public TaskBuilder runOnAllNodes() {
            this.runOnAllNodes = true;
            return this;
        }

        @Override
        public TaskBuilder asSystemTask() {
            if (!persistent) {
                throw new IllegalStateException("System tasks must be persistent. Cannot use asSystemTask() with nonPersistent().");
            }
            this.systemTask = true;
            return this;
        }

        @Override
        public TaskBuilder withMaxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Max retries cannot be negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        @Override
        public TaskBuilder withRetryDelay(long delay, TimeUnit unit) {
            if (delay < 0) {
                throw new IllegalArgumentException("Retry delay cannot be negative");
            }
            this.retryDelay = unit.toMillis(delay);
            return this;
        }

        @Override
        public TaskBuilder withDependencies(String... taskIds) {
            if (taskIds != null) {
                for (String taskId : taskIds) {
                    if (taskId == null || taskId.trim().isEmpty()) {
                        throw new IllegalArgumentException("Task dependency ID cannot be null or empty");
                    }
                    this.dependsOn.add(taskId);
                }
            }
            return this;
        }

        @Override
        public ScheduledTask schedule() {
            if (executor != null) {
                schedulerService.registerTaskExecutor(executor);
            }

            // Check for existing system tasks of the same type if this is a system task
            if (systemTask) {
                List<ScheduledTask> existingTasks = schedulerService.getTasksByType(taskType, 0, 1, null).getList();
                if (!existingTasks.isEmpty() && existingTasks.get(0).isSystemTask()) {
                    // Reuse the existing system task
                    ScheduledTask existingTask = existingTasks.get(0);
                    LOGGER.info("Reusing existing system task: {}", existingTask.getItemId());

                    // Schedule the existing task
                    schedulerService.scheduleTask(existingTask);
                    return existingTask;
                }
            }

            ScheduledTask task = schedulerService.createTask(
                taskType,
                parameters,
                initialDelay,
                period,
                timeUnit,
                fixedRate,
                oneShot,
                allowParallelExecution,
                persistent
            );

            task.setRunOnAllNodes(runOnAllNodes);
            task.setMaxRetries(maxRetries);
            task.setRetryDelay(retryDelay);
            if (!dependsOn.isEmpty()) {
                task.setDependsOn(dependsOn);
            }
            task.setSystemTask(systemTask);
            schedulerService.scheduleTask(task);
            return task;
        }
    }
}


