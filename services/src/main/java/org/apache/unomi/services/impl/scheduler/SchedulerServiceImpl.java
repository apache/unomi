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
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.ScheduledTask.TaskStatus;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final long MAX_RETRY_AGE_MS = 5 * 60 * 1000; // 5 minutes

    private String nodeId;
    private boolean executorNode;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;
    private long lockTimeout = DEFAULT_LOCK_TIMEOUT;
    private long completedTaskTtlDays = DEFAULT_COMPLETED_TASK_TTL_DAYS;
    private boolean purgeTaskEnabled = DEFAULT_PURGE_TASK_ENABLED;
    private ScheduledTask taskPurgeTask;
    private volatile boolean shutdownNow = false;

    private final Map<String, ScheduledTask> nonPersistentTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, Queue<ScheduledTask>> waitingNonPersistentTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean checkTasksRunning = new AtomicBoolean(false);

    // Manager instances - will be injected by Blueprint
    private TaskStateManager stateManager;
    private TaskLockManager lockManager;
    private TaskExecutionManager executionManager;
    private TaskRecoveryManager recoveryManager;
    private TaskMetricsManager metricsManager;
    private TaskHistoryManager historyManager;
    private TaskValidationManager validationManager;
    private TaskExecutorRegistry executorRegistry;

    private BundleContext bundleContext;
    private SchedulerProvider persistenceProvider;

    private final AtomicBoolean servicesInitialized = new AtomicBoolean(false);
    private final CountDownLatch servicesInitializedLatch = new CountDownLatch(1);

    // Pending operations queue
    private final Queue<PendingOperation> pendingOperations = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingPendingOperations = new AtomicBoolean(false);

    /**
     * Finds all persistent tasks that are currently locked (i.e., have a lock owner and are not expired).
     * This is used by the recovery manager to detect tasks that may need to be recovered if their lock has expired.
     */
    public List<ScheduledTask> findLockedTasks() {
        List<ScheduledTask> lockedTasks = new ArrayList<>();

        // Check persistent tasks
        if (persistenceProvider != null) {
            try {
                List<ScheduledTask> persistentLockedTasks = persistenceProvider.getAllTasks().stream()
                    .filter(task -> task.getLockOwner() != null
                        && task.getStatus() != ScheduledTask.TaskStatus.COMPLETED
                        && task.getStatus() != ScheduledTask.TaskStatus.CANCELLED)
                    .collect(Collectors.toList());
                lockedTasks.addAll(persistentLockedTasks);
            } catch (Exception e) {
                LOGGER.error("Error while finding locked persistent tasks", e);
            }
        }

        // Check non-persistent tasks
        List<ScheduledTask> nonPersistentLockedTasks = nonPersistentTasks.values().stream()
            .filter(task -> task.getLockOwner() != null
                && task.getStatus() != ScheduledTask.TaskStatus.COMPLETED
                && task.getStatus() != ScheduledTask.TaskStatus.CANCELLED)
            .collect(Collectors.toList());
        lockedTasks.addAll(nonPersistentLockedTasks);

        return lockedTasks;
    }

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
        private int retryCount = 0;

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

        public int getRetryCount() {
            return retryCount;
        }

        public void incrementRetryCount() {
            retryCount++;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > MAX_RETRY_AGE_MS;
        }

        @Override
        public String toString() {
            return String.format("PendingOperation{type=%s, description='%s', timestamp=%d, retries=%d}",
                type, description, timestamp, retryCount);
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
               executionManager != null &&
               !shutdownNow;
    }

    /**
     * Checks if all required services are initialized and available, including persistence provider if required
     * @param requirePersistenceProvider Whether the operation requires persistence provider to be available
     * @return true if services are ready, false otherwise
     */
    private boolean areServicesReady(boolean requirePersistenceProvider) {
        boolean basicServicesReady = areServicesReady();
        if (!basicServicesReady) {
            return false;
        }

        if (requirePersistenceProvider && persistenceProvider == null) {
            return false;
        }

        return true;
    }

    /**
     * Queues an operation to be executed once services are available
     * @param type The type of operation
     * @param description Human-readable description of the operation
     * @param parameters The parameters for the operation
     */
    private void queuePendingOperation(OperationType type, String description, Object... parameters) {
        queuePendingOperation(type, description, false, parameters);
    }

    /**
     * Queues an operation to be executed once services are available
     * @param type The type of operation
     * @param description Human-readable description of the operation
     * @param requirePersistenceProvider Whether the operation requires persistence provider to be available
     * @param parameters The parameters for the operation
     */
    private void queuePendingOperation(OperationType type, String description, boolean requirePersistenceProvider, Object... parameters) {
        if (shutdownNow) {
            LOGGER.debug("Shutdown in progress, dropping pending operation: {}", description);
            return;
        }

        PendingOperation operation = new PendingOperation(type, description, parameters);
        pendingOperations.offer(operation);
        LOGGER.debug("Queued pending operation: {} (requires persistence: {})", operation, requirePersistenceProvider);

        // Try to process pending operations if services are ready
        if (areServicesReady(requirePersistenceProvider)) {
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
            int skippedCount = 0;

            while (!pendingOperations.isEmpty() && !shutdownNow) {
                PendingOperation operation = pendingOperations.poll();
                if (operation == null) {
                    break;
                }

                // Check if operation has exceeded retry limits or timeout
                if (operation.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                    errorCount++;
                    LOGGER.error("Operation {} exceeded maximum retry attempts ({}), dropping operation",
                        operation.getDescription(), MAX_RETRY_ATTEMPTS);
                    continue;
                }

                if (operation.isExpired()) {
                    errorCount++;
                    LOGGER.error("Operation {} exceeded maximum age ({}ms), dropping operation",
                        operation.getDescription(), MAX_RETRY_AGE_MS);
                    continue;
                }

                // Check if this operation requires persistence provider and if it's available
                boolean requiresPersistence = requiresPersistenceProvider(operation);
                if (requiresPersistence && persistenceProvider == null) {
                    // Re-queue the operation if persistence provider is not available
                    operation.incrementRetryCount();
                    pendingOperations.offer(operation);
                    skippedCount++;
                    LOGGER.debug("Skipping operation {} - persistence provider not available, will retry later (attempt {})",
                        operation.getDescription(), operation.getRetryCount());

                    // Check if all remaining operations require persistence
                    boolean allRemainingRequirePersistence = checkIfAllRemainingOperationsRequirePersistence();
                    if (allRemainingRequirePersistence) {
                        LOGGER.debug("All remaining operations require persistence provider, breaking out of processing loop");
                        break;
                    } else {
                        LOGGER.debug("Some remaining operations don't require persistence, continuing to process them");
                        continue;
                    }
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

            if (processedCount > 0 || errorCount > 0 || skippedCount > 0) {
                LOGGER.info("Processed {} pending operations ({} successful, {} errors, {} skipped due to missing persistence)",
                    processedCount + errorCount + skippedCount, processedCount, errorCount, skippedCount);
            }
        } finally {
            processingPendingOperations.set(false);
        }
    }

    /**
     * Determines if an operation type requires the persistence provider to be available
     * @param operation The pending operation
     * @return true if the operation requires persistence provider, false otherwise
     */
    private boolean requiresPersistenceProvider(PendingOperation operation) {
        switch (operation.getType()) {
            case SCHEDULE_TASK:
                // Check if the task is persistent
                if (operation.getParameters().length > 0) {
                    ScheduledTask task = (ScheduledTask) operation.getParameters()[0];
                    return task != null && task.isPersistent();
                }
                return false;
            case INITIALIZE_TASK_PURGE:
                // Task purge creates a persistent system task
                return true;
            case RECOVER_CRASHED_TASKS:
                // Recovery may need to access persistent tasks
                return true;
            default:
                // Other operations don't require persistence provider
                return false;
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
                executorRegistry.registerExecutor(executor);
                break;

            case UNREGISTER_TASK_EXECUTOR:
                TaskExecutor executorToUnregister = (TaskExecutor) operation.getParameters()[0];
                executorRegistry.unregisterExecutor(executorToUnregister);
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

    // Setter methods for Blueprint dependency injection
    public void setStateManager(TaskStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setLockManager(TaskLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public void setExecutionManager(TaskExecutionManager executionManager) {
        this.executionManager = executionManager;
    }

    public void setRecoveryManager(TaskRecoveryManager recoveryManager) {
        this.recoveryManager = recoveryManager;
    }

    public void setMetricsManager(TaskMetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    public void setHistoryManager(TaskHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    public void setValidationManager(TaskValidationManager validationManager) {
        this.validationManager = validationManager;
    }

    public void setExecutorRegistry(TaskExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    public void setPersistenceProvider(SchedulerProvider persistenceProvider) {
        this.persistenceProvider = persistenceProvider;
        LOGGER.debug("PersistenceSchedulerProvider bound to SchedulerService");

        // Clear any expired operations first
        clearExpiredOperations();

        // Process any pending operations that were waiting for the persistence provider
        if (servicesInitialized.get() && !pendingOperations.isEmpty()) {
            LOGGER.info("Processing {} pending operations that were waiting for persistence provider", pendingOperations.size());
            processPendingOperations();
        }
    }

    /**
     * Checks if all remaining operations in the queue require the persistence provider
     * @return true if all remaining operations require persistence, false otherwise
     */
    private boolean checkIfAllRemainingOperationsRequirePersistence() {
        if (pendingOperations.isEmpty()) {
            return true; // No operations left, so technically all remaining require persistence
        }

        // Create a temporary list to hold operations while we check them
        List<PendingOperation> tempOperations = new ArrayList<>();
        boolean allRequirePersistence = true;
        int totalOperations = 0;
        int operationsRequiringPersistence = 0;

        // Check all operations in the queue
        PendingOperation operation;
        while ((operation = pendingOperations.poll()) != null) {
            tempOperations.add(operation);
            totalOperations++;
            if (requiresPersistenceProvider(operation)) {
                operationsRequiringPersistence++;
            } else {
                allRequirePersistence = false;
            }
        }

        // Put all operations back in the queue
        for (PendingOperation op : tempOperations) {
            pendingOperations.offer(op);
        }

        LOGGER.debug("Queue analysis: {} total operations, {} require persistence, all require persistence: {}",
            totalOperations, operationsRequiringPersistence, allRequirePersistence);

        return allRequirePersistence;
    }

    /**
     * Clears expired operations from the pending operations queue
     * This prevents accumulation of stale operations that can't be processed
     */
    private void clearExpiredOperations() {
        if (pendingOperations.isEmpty()) {
            return;
        }

        int originalSize = pendingOperations.size();
        List<PendingOperation> validOperations = new ArrayList<>();

        PendingOperation operation;
        while ((operation = pendingOperations.poll()) != null) {
            if (operation.isExpired()) {
                LOGGER.warn("Clearing expired operation: {} (age: {}ms)",
                    operation.getDescription(), System.currentTimeMillis() - operation.getTimestamp());
            } else {
                validOperations.add(operation);
            }
        }

        // Re-add valid operations
        for (PendingOperation validOperation : validOperations) {
            pendingOperations.offer(validOperation);
        }

        int clearedCount = originalSize - validOperations.size();
        if (clearedCount > 0) {
            LOGGER.info("Cleared {} expired operations from pending queue", clearedCount);
        }
    }

    public void unsetPersistenceProvider(SchedulerProvider persistenceProvider) {
        this.persistenceProvider = null;
        LOGGER.info("PersistenceSchedulerProvider unbound from SchedulerService");
    }

    /**
     * Purges old completed tasks based on the configured TTL.
     * This method delegates to the persistence provider.
     */
    public void purgeOldTasks() {
        if (persistenceProvider != null) {
            persistenceProvider.purgeOldTasks();
        }
    }

    public void postConstruct() {
        if (bundleContext == null) {
            LOGGER.error("BundleContext is null, cannot initialize service trackers");
            return;
        }

        // Validate that all required managers are injected
        if (stateManager == null || lockManager == null || executionManager == null ||
            recoveryManager == null || metricsManager == null || historyManager == null ||
            validationManager == null || executorRegistry == null) {
            LOGGER.error("Required managers not injected by Blueprint");
            return;
        }

        // Set the scheduler service reference in managers that need it
        lockManager.setSchedulerService(this);
        executionManager.setSchedulerService(this);
        recoveryManager.setSchedulerService(this);

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

        // Mark services as initialized and process any pending operations
        servicesInitialized.set(true);
        servicesInitializedLatch.countDown();

        // Process any pending operations that were queued during initialization
        processPendingOperations();
    }

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
            this.executorRegistry.clear();
            this.nonPersistentTasks.clear();
            this.waitingNonPersistentTasks.clear();
            LOGGER.debug("Task collections cleared");
        } catch (Exception e) {
            LOGGER.debug("Error clearing task collections: {}", e.getMessage());
        }

        LOGGER.info("SchedulerService shutdown completed");
    }

    /**
     * Checks if the scheduler is shutting down.
     * This method is used by TaskExecutionManager to skip task execution during shutdown.
     * @return true if the scheduler is shutting down, false otherwise
     */
    public boolean isShutdownNow() {
        return shutdownNow;
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

            // Clear expired operations periodically to prevent accumulation
            clearExpiredOperations();

            // Check for crashed tasks first
            recoveryManager.recoverCrashedTasks();

            List<ScheduledTask> tasks = new ArrayList<>();
            // Get all enabled tasks that are either scheduled or waiting
            if (persistenceProvider != null) {
                List<ScheduledTask> persistentTasks = persistenceProvider.findEnabledScheduledOrWaitingTasks();
                if (persistentTasks == null) {
                    LOGGER.debug("No tasks found or persistence service unavailable");
                } else {
                    tasks.addAll(persistentTasks);
                }
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

            if (tasks.isEmpty()) {
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
        TaskExecutor executor = executorRegistry.getExecutor(taskType);
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
        // Check non-persistent tasks first (faster - local map lookup)
        boolean hasNonPersistentRunningTask = nonPersistentTasks.values().stream()
            .anyMatch(task -> taskType.equals(task.getTaskType()) &&
                            task.getStatus() == ScheduledTask.TaskStatus.RUNNING &&
                            !lockManager.isLockExpired(task));

        if (hasNonPersistentRunningTask) {
            return true;
        }

        // Check persistent tasks (slower - database query)
        if (persistenceProvider != null) {
        List<ScheduledTask> runningTasks = persistenceProvider.findTasksByTypeAndStatus(taskType, ScheduledTask.TaskStatus.RUNNING);
        return runningTasks.stream().anyMatch(task -> !lockManager.isLockExpired(task));
        }

        return false;
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
        if (areServicesReady(task.isPersistent())) {
            scheduleTaskInternal(task);
        } else {
            queuePendingOperation(OperationType.SCHEDULE_TASK,
                "Schedule task: " + task.getItemId(), task.isPersistent(), new Object[]{task});
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
        TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
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
        if (persistenceProvider == null) {
            return null;
        }

        try {
            return persistenceProvider.getTask(taskId);
        } catch (Exception e) {
            LOGGER.error("Error loading task {}: {}", taskId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ScheduledTask> getPersistentTasks() {
        if (persistenceProvider == null || shutdownNow) {
            return new ArrayList<>();
        }

        try {
            return persistenceProvider.getAllTasks();
        } catch (Exception e) {
            LOGGER.error("Error getting persistent tasks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void registerTaskExecutor(TaskExecutor executor) {
        executorRegistry.registerExecutor(executor);
    }

    @Override
    public void unregisterTaskExecutor(TaskExecutor executor) {
        executorRegistry.unregisterExecutor(executor);
    }

    @Override
    public List<ScheduledTask> getMemoryTasks() {
        return new ArrayList<>(nonPersistentTasks.values());
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

    @Override
    public PartialList<ScheduledTask> getTasksByStatus(TaskStatus status, int offset, int size, String sortBy) {
        if (shutdownNow) {
            return new PartialList<>(new ArrayList<>(), offset, size, 0, PartialList.Relation.EQUAL);
        }

        List<ScheduledTask> allTasks = new ArrayList<>();

        // Get persistent tasks by status
        if (persistenceProvider != null) {
            try {
                PartialList<ScheduledTask> persistentTasks = persistenceProvider.getTasksByStatus(status, 0, -1, sortBy);
                if (persistentTasks != null && persistentTasks.getList() != null) {
                    allTasks.addAll(persistentTasks.getList());
                }
            } catch (Exception e) {
                LOGGER.error("Error getting persistent tasks by status: {}", e.getMessage());
            }
        }

        // Get in-memory tasks by status
        List<ScheduledTask> memoryTasks = nonPersistentTasks.values().stream()
            .filter(task -> task.getStatus() == status)
            .collect(Collectors.toList());
        allTasks.addAll(memoryTasks);

        // Sort the combined list if sortBy is specified
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            sortTasksByField(allTasks, sortBy);
        }

        // Apply pagination
        int totalSize = allTasks.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex;

        if (size == -1) {
            // Return all tasks when size is -1
            toIndex = totalSize;
        } else {
            toIndex = Math.min(offset + size, totalSize);
        }

        List<ScheduledTask> pagedTasks = fromIndex < toIndex ?
            allTasks.subList(fromIndex, toIndex) : new ArrayList<>();

        return new PartialList<>(pagedTasks, offset, size, totalSize,
            totalSize <= offset + (size == -1 ? totalSize : size) ? PartialList.Relation.EQUAL : PartialList.Relation.GREATER_THAN_OR_EQUAL_TO);
    }

    @Override
    public PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy) {
        if (shutdownNow) {
            return new PartialList<>(new ArrayList<>(), offset, size, 0, PartialList.Relation.EQUAL);
        }

        List<ScheduledTask> allTasks = new ArrayList<>();

        // Get persistent tasks by type
        if (persistenceProvider != null) {
            try {
                PartialList<ScheduledTask> persistentTasks = persistenceProvider.getTasksByType(taskType, 0, -1, sortBy);
                if (persistentTasks != null && persistentTasks.getList() != null) {
                    allTasks.addAll(persistentTasks.getList());
                }
            } catch (Exception e) {
                LOGGER.error("Error getting persistent tasks by type: {}", e.getMessage());
            }
        }

        // Get in-memory tasks by type
        List<ScheduledTask> memoryTasks = nonPersistentTasks.values().stream()
            .filter(task -> taskType.equals(task.getTaskType()))
            .collect(Collectors.toList());
        allTasks.addAll(memoryTasks);

        // Sort the combined list if sortBy is specified
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            sortTasksByField(allTasks, sortBy);
        }

        // Apply pagination
        int totalSize = allTasks.size();
        int fromIndex = Math.min(offset, totalSize);
        int toIndex;

        if (size == -1) {
            // Return all tasks when size is -1
            toIndex = totalSize;
        } else {
            toIndex = Math.min(offset + size, totalSize);
        }

        List<ScheduledTask> pagedTasks = fromIndex < toIndex ?
            allTasks.subList(fromIndex, toIndex) : new ArrayList<>();

        return new PartialList<>(pagedTasks, offset, size, totalSize,
            totalSize <= offset + (size == -1 ? totalSize : size) ? PartialList.Relation.EQUAL : PartialList.Relation.GREATER_THAN_OR_EQUAL_TO);
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
            TaskExecutor executor = executorRegistry.getExecutor(task.getTaskType());
            if (executor != null && executor.canResume(task)) {
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
                scheduleTaskInternal(task);
            }
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
            LOGGER.info("Task purge is disabled, skipping initialization");
            return;
        }

        // Check if persistence provider is available (required for task purge)
        if (persistenceProvider == null) {
            LOGGER.warn("Persistence provider not available, cannot initialize task purge. Will retry when persistence becomes available.");
            return;
        }

        LOGGER.info("Initializing task purge with TTL: {} days", completedTaskTtlDays);

        // Register the task executor for task purge
        TaskExecutor taskPurgeExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "task-purge";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                LOGGER.info("Purge task executor called - starting purge of old tasks");
                try {
                    if (persistenceProvider != null) {
                        LOGGER.info("Calling persistenceProvider.purgeOldTasks() with TTL: {} days", completedTaskTtlDays);
                        persistenceProvider.purgeOldTasks();
                        LOGGER.info("Purge task completed successfully");
                    } else {
                        LOGGER.warn("Persistence provider is null, cannot purge tasks");
                    }
                    callback.complete();
                } catch (Throwable t) {
                    LOGGER.error("Error while purging old tasks", t);
                    callback.fail(t.getMessage());
                }
            }
        };

        registerTaskExecutor(taskPurgeExecutor);
        LOGGER.info("Registered purge task executor");

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
            if (persistenceProvider == null) {
                LOGGER.warn("Cannot save task {} of type {}- persistence service unavailable", task.getItemId(), task.getTaskType());
                return false;
            }

            try {
                persistenceProvider.saveTask(task);
                LOGGER.debug("Saved task {} to persistence", task.getItemId());
                return true;
            } catch (Exception e) {
                LOGGER.error("Error saving task {} to persistence", task.getItemId(), e);
                return false;
            }
        } else {
            LOGGER.debug("Saving task {} of type {} in memory", task.getItemId(), task.getTaskType());
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

    @Override
    public List<ScheduledTask> findTasksByStatus(TaskStatus taskStatus) {
        if (shutdownNow) {
            return new ArrayList<>();
        }

        List<ScheduledTask> allTasks = new ArrayList<>();

        // Get persistent tasks by status
        if (persistenceProvider != null) {
            try {
                List<ScheduledTask> persistentTasks = persistenceProvider.findTasksByStatus(taskStatus);
                if (persistentTasks != null) {
                    allTasks.addAll(persistentTasks);
                }
            } catch (Exception e) {
                LOGGER.error("Error finding persistent tasks by status: {}", e.getMessage());
            }
        }

        // Get in-memory tasks by status
        List<ScheduledTask> memoryTasks = nonPersistentTasks.values().stream()
            .filter(task -> task.getStatus() == taskStatus)
            .collect(Collectors.toList());
        allTasks.addAll(memoryTasks);

        return allTasks;
    }

    /**
     * Sorts tasks by the specified field.
     * Supports common task fields like creationDate, lastExecutionDate, nextScheduledExecution, etc.
     *
     * @param tasks The list of tasks to sort
     * @param sortBy The field to sort by (with optional :asc or :desc suffix)
     */
    private void sortTasksByField(List<ScheduledTask> tasks, String sortBy) {
        if (tasks == null || tasks.isEmpty() || sortBy == null || sortBy.trim().isEmpty()) {
            return;
        }

        String field = sortBy.trim();
        boolean ascending = true;

        // Check for sort direction suffix
        if (field.endsWith(":desc")) {
            field = field.substring(0, field.length() - 5);
            ascending = false;
        } else if (field.endsWith(":asc")) {
            field = field.substring(0, field.length() - 4);
            ascending = true;
        }

        final String finalField = field;
        final boolean finalAscending = ascending;

        tasks.sort((t1, t2) -> {
            int comparison = 0;

            switch (finalField.toLowerCase()) {
                case "creationdate":
                    comparison = compareDates(t1.getCreationDate(), t2.getCreationDate());
                    break;
                case "lastexecutiondate":
                    comparison = compareDates(t1.getLastExecutionDate(), t2.getLastExecutionDate());
                    break;
                case "nextscheduledexecution":
                    comparison = compareDates(t1.getNextScheduledExecution(), t2.getNextScheduledExecution());
                    break;
                case "tasktype":
                    comparison = compareStrings(t1.getTaskType(), t2.getTaskType());
                    break;
                case "status":
                    comparison = t1.getStatus().compareTo(t2.getStatus());
                    break;
                case "itemid":
                    comparison = compareStrings(t1.getItemId(), t2.getItemId());
                    break;
                case "failurecount":
                    comparison = Integer.compare(t1.getFailureCount(), t2.getFailureCount());
                    break;
                case "successcount":
                    comparison = Integer.compare(t1.getSuccessCount(), t2.getSuccessCount());
                    break;
                case "totalexecutioncount":
                    comparison = Integer.compare(t1.getSuccessCount() + t1.getFailureCount(),
                                               t2.getSuccessCount() + t2.getFailureCount());
                    break;
                default:
                    // Default to creation date if field is not recognized
                    comparison = compareDates(t1.getCreationDate(), t2.getCreationDate());
                    break;
            }

            return finalAscending ? comparison : -comparison;
        });
    }

    /**
     * Compares two dates, handling null values.
     * Null dates are considered less than non-null dates.
     */
    private int compareDates(Date date1, Date date2) {
        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return -1;
        if (date2 == null) return 1;
        return date1.compareTo(date2);
    }

    /**
     * Compares two strings, handling null values.
     * Null strings are considered less than non-null strings.
     */
    private int compareStrings(String str1, String str2) {
        if (str1 == null && str2 == null) return 0;
        if (str1 == null) return -1;
        if (str2 == null) return 1;
        return str1.compareTo(str2);
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
     * Refreshes the task indices to ensure up-to-date view.
     * This is used by the distributed locking mechanism to ensure
     * all nodes see the latest task state.
     */
    public void refreshTasks() {
        if (persistenceProvider != null) {
            persistenceProvider.refreshTasks();
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
            if (persistenceProvider == null) {
                LOGGER.warn("Cannot save task with refresh - persistence service unavailable");
                return false;
            }

            try {
                // Save with optimistic concurrency control
                // Refresh is now handled automatically by the refresh policy
                return persistenceProvider.saveTask(task);
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
        if (persistenceProvider != null) {
            return persistenceProvider.getActiveNodes();
        }
        return new ArrayList<>();
    }

    /**
     * Simulates a crash of the scheduler service by abruptly stopping all operations.
     * This is used for testing crash recovery scenarios.
     */
    public void simulateCrash() {
        shutdownNow = true;
        running.set(false);

        // Release any locks owned by this node (check both persistent and non-persistent tasks)
        List<ScheduledTask> tasksToRelease = new ArrayList<>();

        // Check persistent tasks
        if (persistenceProvider != null) {
            try {
                List<ScheduledTask> persistentTasks = persistenceProvider.findTasksByLockOwner(nodeId);
                tasksToRelease.addAll(persistentTasks);
            } catch (Exception e) {
                LOGGER.warn("Error finding locked persistent tasks during crash simulation: {}", e.getMessage());
            }
        }

        // Check non-persistent tasks
        List<ScheduledTask> nonPersistentLockedTasks = nonPersistentTasks.values().stream()
            .filter(task -> nodeId.equals(task.getLockOwner()))
            .collect(Collectors.toList());
        tasksToRelease.addAll(nonPersistentLockedTasks);

        // Release all locks
        for (ScheduledTask task : tasksToRelease) {
                    try {
                        lockManager.releaseLock(task);
                    } catch (Exception e) {
                        LOGGER.debug("Error releasing lock for task {} during crash simulation: {}", task.getItemId(), e.getMessage());
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

    public TaskLockManager getLockManager() {
        return lockManager;
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


