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

/**
 * Implementation of the SchedulerService that provides task scheduling and execution capabilities.
 * This implementation supports:
 * - Persistent and in-memory tasks
 * - Single-node and cluster execution
 * - Task dependencies and waiting queues
 * - Lock management and crash recovery
 * - Execution history and metrics tracking
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
 * @author dgaillard
 */
public class SchedulerServiceImpl implements SchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class.getName());
    private static final long DEFAULT_LOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private static final long DEFAULT_COMPLETED_TASK_TTL_DAYS = 30; // 30 days default retention for completed tasks
    private static final boolean DEFAULT_PURGE_TASK_ENABLED = true;
    private static final int MIN_THREAD_POOL_SIZE = 4;

    private String nodeId;
    private boolean executorNode;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;
    private long lockTimeout = DEFAULT_LOCK_TIMEOUT;
    private long completedTaskTtlDays = DEFAULT_COMPLETED_TASK_TTL_DAYS;
    private boolean purgeTaskEnabled = DEFAULT_PURGE_TASK_ENABLED;
    private ScheduledTask taskPurgeTask;

    // Core services
    private PersistenceService persistenceService;
    private ClusterService clusterService;
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
    private TaskMetricsManager metricsManager;
    private TaskHistoryManager historyManager;
    private TaskValidationManager validationManager;

    private BundleContext bundleContext;
    private ServiceTracker<TaskExecutor, TaskExecutor> taskExecutorTracker;

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

    @PostConstruct
    public void postConstruct() {
        // Initialize managers
        this.metricsManager = new TaskMetricsManager();
        this.stateManager = new TaskStateManager();
        this.lockManager = new TaskLockManager(nodeId, lockTimeout, metricsManager, this);
        this.historyManager = new TaskHistoryManager(nodeId, metricsManager);
        this.validationManager = new TaskValidationManager();
        this.executionManager = new TaskExecutionManager(nodeId, threadPoolSize, stateManager,
            lockManager, metricsManager, historyManager, persistenceService, this);
        this.recoveryManager = new TaskRecoveryManager(nodeId, persistenceService, stateManager,
            lockManager, metricsManager, executionManager, this);

        if (executorNode) {
            running.set(true);
            // Start task checking thread using the execution manager
            executionManager.startTaskChecker(this::checkTasks);
            // Initialize purge tasks
            initializePurgeTasks();
        }

        if (nodeId == null) {
            nodeId = UUID.randomUUID().toString();
        }

        LOGGER.info("Scheduler service initialized. Node ID: {}, Executor node: {}, Thread pool size: {}",
            nodeId, executorNode, Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize));

        // Initialize service tracker for TaskExecutors
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

    void simulateCrash() {
        LOGGER.info("Simulating crash of node {}", nodeId);
        running.set(false);
        if (taskPurgeTask != null) {
            cancelTask(taskPurgeTask.getItemId());
        }

        taskExecutors.clear();
        executionManager.shutdown();
    }

    @PreDestroy
    public void preDestroy() {
        running.set(false);

        // Release any task locks owned by this node
        if (persistenceService != null) {
            List<ScheduledTask> tasks = findTasksByLockOwner(nodeId);
            for (ScheduledTask task : tasks) {
                lockManager.releaseLock(task);
            }
        }

        if (taskPurgeTask != null) {
            cancelTask(taskPurgeTask.getItemId());
        }

        // Shutdown execution manager
        executionManager.shutdown();

        if (taskExecutorTracker != null) {
            taskExecutorTracker.close();
            taskExecutorTracker = null;
        }
    }

    void checkTasks() {
        if (!running.get() || persistenceService == null) {
            return;
        }

        // Add reentrant check
        if (!checkTasksRunning.compareAndSet(false, true)) {
            LOGGER.debug("checkTasks is already running, skipping this execution");
            return;
        }

        try {
            // Check for crashed tasks first
            recoveryManager.recoverCrashedTasks();

            // Get all enabled tasks that are either scheduled or waiting
            List<ScheduledTask> tasks = findEnabledScheduledOrWaitingTasks();

            // Also check in-memory tasks
            List<ScheduledTask> inMemoryTasks = nonPersistentTasks.values().stream()
                .filter(task -> task.isEnabled() &&
                    (task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED ||
                     task.getStatus() == ScheduledTask.TaskStatus.WAITING))
                .collect(Collectors.toList());

            // Add in-memory tasks to the list of tasks to check
            if (!inMemoryTasks.isEmpty()) {
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
                processTaskGroup(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            LOGGER.error("Error checking tasks", e);
        } finally {
            checkTasksRunning.set(false);
        }
    }

    private List<ScheduledTask> findTasksByLockOwner(String owner) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", "lockOwner");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", owner);
        return persistenceService.query(condition, null, ScheduledTask.class, 0, -1).getList();
    }

    private List<ScheduledTask> findEnabledScheduledOrWaitingTasks() {
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

        return persistenceService.query(andCondition, "creationDate:asc", ScheduledTask.class, 0, -1).getList();
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

        return persistenceService.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
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
        ScheduledTask task = persistenceService.load(taskId, ScheduledTask.class);
        return task != null ? task : nonPersistentTasks.get(taskId);
    }

    @Override
    public List<ScheduledTask> getPersistentTasks() {
        return persistenceService.getAllItems(ScheduledTask.class, 0, -1, null).getList();
    }

    @Override
    public List<ScheduledTask> getMemoryTasks() {
        return new ArrayList<>(nonPersistentTasks.values());
    }

    @Override
    public void registerTaskExecutor(TaskExecutor executor) {
        executionManager.registerTaskExecutor(executor);
    }

    @Override
    public void unregisterTaskExecutor(TaskExecutor executor) {
        executionManager.unregisterTaskExecutor(executor);
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

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
        // Handle case where the ClusterService is set after SchedulerService is initialized
        if (clusterService instanceof org.apache.unomi.services.impl.cluster.ClusterServiceImpl && running.get()) {
            LOGGER.info("ClusterService set after initialization. Initializing ClusterService scheduled tasks...");
            try {
                ((org.apache.unomi.services.impl.cluster.ClusterServiceImpl) clusterService).initializeScheduledTasks();
            } catch (Exception e) {
                LOGGER.error("Error initializing ClusterService scheduled tasks", e);
            }
        }
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
        if (executorNode) {
            recoveryManager.recoverCrashedTasks();
        }
    }

    @Override
    public void retryTask(String taskId, boolean resetFailureCount) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.FAILED) {
            if (resetFailureCount) {
                task.setFailureCount(0);
            }
            task.setLastExecutionDate(null); // we have to do this to force the task to execute again
            stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RETRIED);
            scheduleTask(task);
        }
    }

    @Override
    public void resumeTask(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            TaskExecutor executor = executionManager.getTaskExecutor(task.getTaskType());
            if (executor != null && executor.canResume(task)) {
                stateManager.updateTaskState(task, ScheduledTask.TaskStatus.SCHEDULED, null, nodeId);
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_RESUMED);
                scheduleTask(task);
            }
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", "status");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", status.toString());
        return persistenceService.query(condition, sortBy, ScheduledTask.class, offset, size);
    }

    @Override
    public PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", "taskType");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", taskType);
        return persistenceService.query(condition, sortBy, ScheduledTask.class, offset, size);
    }

    private void initializePurgeTasks() {
        if (!purgeTaskEnabled) {
            return;
        }

        taskPurgeTask = newTask("task-purge")
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .withSimpleExecutor(() -> purgeOldTasks())
            .schedule();
    }

    void purgeOldTasks() {
        if (!executorNode) {
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

            persistenceService.removeByQuery(andCondition, ScheduledTask.class);
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
    public boolean saveTask(ScheduledTask task) {
        if (task == null) {
            LOGGER.warn("Attempted to save null task, ignoring");
            return false;
        }

        if (task.isPersistent()) {
            try {
                persistenceService.save(task);
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
        return metricsManager.getAllMetrics();
    }

    /**
     * Refreshes the task indices to ensure up-to-date view.
     * This is used by the distributed locking mechanism to ensure
     * all nodes see the latest task state.
     */
    public void refreshTasks() {
        if (persistenceService != null) {
            try {
                persistenceService.refreshIndex(ScheduledTask.class);
            } catch (Exception e) {
                LOGGER.error("Error refreshing task indices", e);
            }
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
        if (task == null) {
            return false;
        }

        if (task.isPersistent()) {
            try {
                // Save with optimistic concurrency control
                // Refresh is now handled automatically by the refresh policy
                return persistenceService.save(task);
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
        if (clusterService != null) {
            try {
                List<ClusterNode> clusterNodes = clusterService.getClusterNodes();
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
        try {
            // Create a condition to find tasks with recent locks
            Condition recentLocksCondition = new Condition();
            recentLocksCondition.setConditionType(PROPERTY_CONDITION_TYPE);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("propertyName", "lockDate");
            parameters.put("comparisonOperator", "exists");
            recentLocksCondition.setParameterValues(parameters);
            
            // Query for tasks with lock information
            List<ScheduledTask> recentlyLockedTasks = persistenceService.query(recentLocksCondition, "lockDate", ScheduledTask.class);
            
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
        
        LOGGER.debug("Detected active cluster nodes: {}", activeNodes);
        return new ArrayList<>(activeNodes);
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
            schedulerService.scheduleTask(task);
            return task;
        }
    }
}

