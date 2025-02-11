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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.ScheduledTask.TaskStatus;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author dgaillard
 */
public class SchedulerServiceImpl implements SchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class.getName());
    private static final long LOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    private static final long TASK_CHECK_INTERVAL = 1000; // 1 second
    private static final long DEFAULT_COMPLETED_TASK_TTL_DAYS = 30; // 30 days default retention for completed tasks
    private static final boolean DEFAULT_PURGE_TASK_ENABLED = true;
    private static final int MIN_THREAD_POOL_SIZE = 4;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService sharedScheduler;
    private int threadPoolSize = MIN_THREAD_POOL_SIZE;
    private boolean executorNode;
    private String nodeId;
    private PersistenceService persistenceService;
    private final Map<String, TaskExecutor> taskExecutors = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledTask> nonPersistentTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long completedTaskTtlDays = DEFAULT_COMPLETED_TASK_TTL_DAYS;
    private boolean purgeTaskEnabled = DEFAULT_PURGE_TASK_ENABLED;
    private ScheduledTask taskPurgeTask;
    private DefinitionsService definitionsService;

    private static final ConditionType PROPERTY_CONDITION_TYPE = new ConditionType() {
        private String queryBuilder = "propertyConditionESQueryBuilder";
        private String conditionEvaluator = "propertyConditionEvaluator";

        @Override
        public String getItemId() {
            return "propertyCondition";
        }

        @Override
        public String getItemType() {
            return ITEM_TYPE;
        }

        @Override
        public String getQueryBuilder() {
            return queryBuilder;
        }

        @Override
        public void setQueryBuilder(String queryBuilder) {
            this.queryBuilder = queryBuilder;
        }

        @Override
        public String getConditionEvaluator() {
            return conditionEvaluator;
        }

        @Override
        public void setConditionEvaluator(String conditionEvaluator) {
            this.conditionEvaluator = conditionEvaluator;
        }

        @Override
        public Long getVersion() {
            return 1L;
        }
    };

    private static final ConditionType BOOLEAN_CONDITION_TYPE = new ConditionType() {
        private String queryBuilder = "booleanConditionESQueryBuilder";
        private String conditionEvaluator = "booleanConditionEvaluator";

        @Override
        public String getItemId() {
            return "booleanCondition";
        }

        @Override
        public String getItemType() {
            return ITEM_TYPE;
        }

        @Override
        public String getQueryBuilder() {
            return queryBuilder;
        }

        @Override
        public void setQueryBuilder(String queryBuilder) {
            this.queryBuilder = queryBuilder;
        }

        @Override
        public String getConditionEvaluator() {
            return conditionEvaluator;
        }

        @Override
        public void setConditionEvaluator(String conditionEvaluator) {
            this.conditionEvaluator = conditionEvaluator;
        }

        @Override
        public Long getVersion() {
            return 1L;
        }
    };

    public void postConstruct() {
        // Initialize shared scheduler with fixed thread pool for parallel execution
        sharedScheduler = Executors.newScheduledThreadPool(Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize),
            r -> {
                Thread t = new Thread(r);
                t.setName("UnomiSharedScheduler-" + t.getId());
                t.setDaemon(true);
                return t;
            });
        nodeId = UUID.randomUUID().toString();

        if (executorNode) {
            running.set(true);
            // Start task checking thread
            scheduler.scheduleAtFixedRate(this::checkTasks, 0, TASK_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            // Initialize purge tasks
            initializePurgeTasks();
        }

        LOGGER.info("Scheduler service initialized. Node ID: {}, Executor node: {}, Thread pool size: {}",
            nodeId, executorNode, Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize));
    }

    public void preDestroy() {
        running.set(false);

        // Cancel all running tasks
        for (ScheduledFuture<?> future : runningTasks.values()) {
            future.cancel(true);
        }
        runningTasks.clear();
        nonPersistentTasks.clear();

        // Release any task locks owned by this node
        if (persistenceService != null) {
            List<ScheduledTask> tasks = persistenceService.query("lockOwner", nodeId, null, ScheduledTask.class, 0, -1).getList();
            for (ScheduledTask task : tasks) {
                releaseLock(task);
            }
        }

        if (taskPurgeTask != null) {
            cancelTask(taskPurgeTask.getItemId());
        }

        sharedScheduler.shutdown();
        scheduler.shutdown();
        LOGGER.info("Scheduler service shutdown.");
    }

    private void checkTasks() {
        if (!running.get() || persistenceService == null) {
            return;
        }

        try {
            // Check for crashed tasks first
            recoverCrashedTasks();

            // Get all enabled tasks
            Condition enabledCondition = createPropertyCondition("enabled", true);
            List<ScheduledTask> tasks = persistenceService.query(enabledCondition, null, ScheduledTask.class, 0, -1).getList();

            for (ScheduledTask task : tasks) {
                if (!shouldExecuteTask(task)) {
                    continue;
                }

                // Try to acquire lock
                if (acquireLock(task)) {
                    scheduleTask(task);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error checking tasks", e);
        }
    }

    private boolean shouldExecuteTask(ScheduledTask task) {
        // Check if task is already running and doesn't allow parallel execution
        if (!task.isAllowParallelExecution() && task.getLockOwner() != null) {
            // Check if lock has expired
            if (task.getLockDate() != null &&
                System.currentTimeMillis() - task.getLockDate().getTime() > LOCK_TIMEOUT) {
                // Lock has expired, mark task as crashed
                task.setLockOwner(null);
                task.setLockDate(null);
                task.setStatus(ScheduledTask.TaskStatus.CRASHED);
                Map<Item,Map> updates = new HashMap<>();
                updates.put(task, Collections.emptyMap());
                if (task.isPersistent()) {
                    persistenceService.update(updates, ScheduledTask.class);
                }
            } else {
                return false;
            }
        }

        // Check if task is already scheduled on this node
        if (!task.isAllowParallelExecution() && runningTasks.containsKey(task.getItemId())) {
            return false;
        }

        // Check if we have an executor for this task type
        return taskExecutors.containsKey(task.getTaskType());
    }

    private boolean acquireLock(ScheduledTask task) {
        // If parallel execution is allowed, no need to acquire a lock
        if (task.isAllowParallelExecution()) {
            task.setStatus(ScheduledTask.TaskStatus.RUNNING);
            if (task.isPersistent()) {
                Map<Item,Map> updates = new HashMap<>();
                updates.put(task, Collections.emptyMap());
                persistenceService.update(updates, ScheduledTask.class);
            }
            return true;
        }

        // For non-parallel tasks, try to acquire a lock
        task.setLockOwner(nodeId);
        task.setLockDate(new Date());
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        Map<Item,Map> updates = new HashMap<>();
        updates.put(task, Collections.emptyMap());
        if (task.isPersistent()) {
            return persistenceService.update(updates, ScheduledTask.class).isEmpty();
        }
        return true;
    }

    @Override
    public void scheduleTask(ScheduledTask task) {
        if (!task.isEnabled()) {
            return;
        }

        // Store non-persistent tasks in memory
        if (!task.isPersistent()) {
            nonPersistentTasks.put(task.getItemId(), task);
        } else {
            persistenceService.save(task);
        }

        // For tasks that should run on all nodes, or if we're the executor node
        if (task.isRunOnAllNodes() || executorNode) {
            TaskExecutor executor = taskExecutors.get(task.getTaskType());
            if (executor == null) {
                LOGGER.warn("No executor found for task type: {}", task.getTaskType());
                return;
            }

            TaskExecutor.TaskStatusCallback statusCallback = createStatusCallback(task);
            Runnable taskWrapper = createTaskWrapper(task, executor, statusCallback);
            ScheduledFuture<?> future;

            // Always use sharedScheduler for tasks that allow parallel execution
            ScheduledExecutorService targetScheduler = task.isAllowParallelExecution() ? sharedScheduler : scheduler;

            // Schedule the task based on its configuration
            if (task.isOneShot()) {
                long delay = task.getStatus() == ScheduledTask.TaskStatus.FAILED ? task.getRetryDelay() : task.getInitialDelay();
                future = targetScheduler.schedule(taskWrapper, delay, task.getTimeUnit());
            } else if (task.getPeriod() > 0) {
                if (task.isFixedRate()) {
                    future = targetScheduler.scheduleAtFixedRate(taskWrapper, task.getInitialDelay(), task.getPeriod(), task.getTimeUnit());
                } else {
                    future = targetScheduler.scheduleWithFixedDelay(taskWrapper, task.getInitialDelay(), task.getPeriod(), task.getTimeUnit());
                }
            } else {
                // Default to single execution for tasks without a period
                future = targetScheduler.schedule(taskWrapper, task.getInitialDelay(), task.getTimeUnit());
            }

            runningTasks.put(task.getItemId(), future);
            updateNextScheduledExecution(task);
        }
    }

    private void updateTaskInPersistence(ScheduledTask task) {
        if (!task.isPersistent()) {
            // Update the task in memory
            nonPersistentTasks.put(task.getItemId(), task);
            return;
        }
        Map<Item,Map> updates = new HashMap<>();
        updates.put(task, Collections.emptyMap());
        persistenceService.update(updates, ScheduledTask.class);
    }

    private Condition createPropertyCondition(String propertyName, Object propertyValue) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", "equals");
        if (propertyValue instanceof Boolean) {
            condition.setParameter("propertyValueInteger", ((Boolean) propertyValue) ? 1 : 0);
        } else {
            condition.setParameter("propertyValue", propertyValue);
        }
        return condition;
    }

    private Condition createExistsCondition(String propertyName) {
        Condition condition = new Condition(PROPERTY_CONDITION_TYPE);
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", "exists");
        return condition;
    }

    private void releaseLock(ScheduledTask task) {
        // Only release lock if parallel execution is not allowed
        if (!task.isAllowParallelExecution()) {
            task.setLockOwner(null);
            task.setLockDate(null);
            updateTaskInPersistence(task);
        }
    }

    private void markTaskAsCrashed(ScheduledTask task) {
        task.setStatus(ScheduledTask.TaskStatus.CRASHED);
        releaseLock(task);
    }

    private boolean isLockExpired(ScheduledTask task) {
        return task.getLockDate() != null &&
               System.currentTimeMillis() - task.getLockDate().getTime() > LOCK_TIMEOUT;
    }

    private TaskExecutor.TaskStatusCallback createStatusCallback(ScheduledTask task) {
        return new TaskExecutor.TaskStatusCallback() {
            @Override
            public void updateStep(String step, Map<String, Object> details) {
                task.setCurrentStep(step);
                task.setStatusDetails(details);
                updateTaskInPersistence(task);
            }

            @Override
            public void checkpoint(Map<String, Object> checkpointData) {
                task.setCheckpointData(checkpointData);
                updateTaskInPersistence(task);
            }

            @Override
            public void updateStatusDetails(Map<String, Object> details) {
                task.setStatusDetails(details);
                updateTaskInPersistence(task);
            }

            @Override
            public void complete() {
                task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
                task.setLastExecutionDate(new Date());
                task.setLastExecutedBy(nodeId);
                task.setLastError(null);
                task.setFailureCount(0);
                if (task.isOneShot()) {
                    task.setEnabled(false);
                }
                updateTaskInPersistence(task);
            }

            @Override
            public void fail(String error) {
                task.setStatus(ScheduledTask.TaskStatus.FAILED);
                task.setLastError(error);
                task.setFailureCount(task.getFailureCount() + 1);
                updateTaskInPersistence(task);
            }
        };
    }

    private Runnable createTaskWrapper(ScheduledTask task, TaskExecutor executor, TaskExecutor.TaskStatusCallback statusCallback) {
        return () -> {
            try {
                // Set task status to RUNNING before execution
                task.setStatus(ScheduledTask.TaskStatus.RUNNING);
                if (task.isPersistent()) {
                    Map<Item,Map> updates = new HashMap<>();
                    updates.put(task, Collections.emptyMap());
                    persistenceService.update(updates, ScheduledTask.class);
                }

                // Execute or resume the task
                if (task.getStatus() == ScheduledTask.TaskStatus.CRASHED && executor.canResume(task)) {
                    executor.resume(task, statusCallback);
                } else {
                    executor.execute(task, statusCallback);
                }
            } catch (Exception e) {
                LOGGER.error("Error executing task: " + task.getItemId(), e);
                statusCallback.fail(e.getMessage());
            } finally {
                releaseLock(task);
            }
        };
    }

    private void updateNextScheduledExecution(ScheduledTask task) {
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() +
            task.getTimeUnit().toMillis(task.getStatus() == ScheduledTask.TaskStatus.FAILED ?
                task.getRetryDelay() : task.getInitialDelay())));
        if (task.isPersistent()) {
            updateTaskInPersistence(task);
        }
    }

    @Override
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }

        // Check both persistent and memory tasks
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setEnabled(false);
            task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
            Map<Item,Map> updates = new HashMap<>();
            updates.put(task, Collections.emptyMap());
            if (task.isPersistent()) {
                persistenceService.update(updates, ScheduledTask.class);
            }
            // Remove from memory tasks if applicable
            nonPersistentTasks.remove(taskId);
        }
    }

    @Override
    public ScheduledTask createTask(String taskType, Map<String, Object> parameters,
                                  long initialDelay, long period, TimeUnit timeUnit,
                                  boolean fixedRate, boolean oneShot, boolean allowParallelExecution, boolean persistent) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(UUID.randomUUID().toString());
        task.setTaskType(taskType);
        task.setParameters(parameters);
        task.setInitialDelay(initialDelay);
        task.setPeriod(period);
        task.setTimeUnit(timeUnit);
        task.setFixedRate(fixedRate);
        task.setOneShot(oneShot);
        task.setAllowParallelExecution(allowParallelExecution);
        task.setEnabled(true);
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        task.setPersistent(persistent);

        if (task.isPersistent()) {
            persistenceService.save(task);
        }
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
        // First check persistent storage
        ScheduledTask task = persistenceService.load(taskId, ScheduledTask.class);
        // If not found in storage, check memory
        if (task == null) {
            task = nonPersistentTasks.get(taskId);
        }
        return task;
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
        taskExecutors.put(executor.getTaskType(), executor);
    }

    @Override
    public void unregisterTaskExecutor(TaskExecutor executor) {
        taskExecutors.remove(executor.getTaskType());
    }

    @Override
    public boolean isExecutorNode() {
        return executorNode;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public ScheduledExecutorService getScheduleExecutorService() {
        return scheduler;
    }

    @Override
    public ScheduledExecutorService getSharedScheduleExecutorService() {
        return sharedScheduler;
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

    public static long getTimeDiffInSeconds(int hourInUtc, ZonedDateTime now) {
        ZonedDateTime nextRun = now.withHour(hourInUtc).withMinute(0).withSecond(0);
        if(now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }

    @Override
    public void recoverCrashedTasks() {
        if (!executorNode) {
            return;
        }

        try {
            // Find tasks that are marked as running but have expired locks
            List<ScheduledTask> runningTasks = persistenceService.query(
                createPropertyCondition("status", ScheduledTask.TaskStatus.RUNNING),
                null, ScheduledTask.class, 0, -1).getList();

            for (ScheduledTask task : runningTasks) {
                if (isLockExpired(task)) {
                    markTaskAsCrashed(task);
                    if (task.getCheckpointData() != null) {
                        resumeTask(task.getItemId());
                    }
                }
            }

            // Check for tasks with expired locks but not marked as running
            List<ScheduledTask> lockedTasks = persistenceService.query(
                createExistsCondition("lockOwner"),
                null, ScheduledTask.class, 0, -1).getList();

            for (ScheduledTask task : lockedTasks) {
                if (isLockExpired(task)) {
                    markTaskAsCrashed(task);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error recovering crashed tasks", e);
        }
    }

    @Override
    public void retryTask(String taskId, boolean resetFailureCount) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.FAILED) {
            if (resetFailureCount) {
                task.setFailureCount(0);
            }
            task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
            task.setLastError(null);
            updateTaskInPersistence(task);
            scheduleTask(task);
        }
    }

    @Override
    public void resumeTask(String taskId) {
        ScheduledTask task = getTask(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            TaskExecutor executor = taskExecutors.get(task.getTaskType());
            if (executor != null && executor.canResume(task)) {
                task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
                updateTaskInPersistence(task);
                scheduleTask(task);
            }
        }
    }

    @Override
    public void updateTaskConfig(String taskId, int maxRetries, long retryDelay) {
        ScheduledTask task = getTask(taskId);
        if (task != null) {
            task.setMaxRetries(maxRetries);
            task.setRetryDelay(retryDelay);
            updateTaskInPersistence(task);
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy) {
        return persistenceService.query(createPropertyCondition("status", status), sortBy, ScheduledTask.class, offset, size);
    }

    @Override
    public PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy) {
        return persistenceService.query(createPropertyCondition("taskType", taskType), sortBy, ScheduledTask.class, offset, size);
    }

    /**
     * Creates a simple recurring task with default settings.
     * Useful for services that just need periodic execution.
     */
    public ScheduledTask createRecurringTask(String taskType, long period, TimeUnit timeUnit, Runnable runnable, boolean persistent) {
        // Create a simple executor for the runnable
        TaskExecutor executor = new TaskExecutor() {
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

        // Register the executor
        registerTaskExecutor(executor);

        // Create and schedule the task
        ScheduledTask task = createTask(
            taskType,
            Collections.emptyMap(),
            0,
            period,
            timeUnit,
            true,  // fixedRate
            false, // not oneShot
            true,   // allow parallel execution
            persistent
        );

        scheduleTask(task);
        return task;
    }

    /**
     * Builder class to simplify task creation with fluent API
     */
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

            schedulerService.scheduleTask(task);
            return task;
        }
    }

    /**
     * Creates a new task builder for fluent task creation
     */
    public TaskBuilder newTask(String taskType) {
        return new TaskBuilder(this, taskType);
    }

    public void setCompletedTaskTtlDays(long completedTaskTtlDays) {
        this.completedTaskTtlDays = completedTaskTtlDays;
    }

    private void initializePurgeTasks() {
        if (!purgeTaskEnabled) {
            return;
        }
        // Schedule task to purge old completed tasks
        taskPurgeTask = newTask("task-purge")
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .withSimpleExecutor(() -> purgeOldTasks())
            .schedule();
    }

    private void purgeOldTasks() {
        if (!executorNode) {
            return;
        }

        try {
            LOGGER.debug("Starting purge of old completed tasks");
            long purgeBeforeTime = System.currentTimeMillis() - (completedTaskTtlDays * 24 * 60 * 60 * 1000);
            Date purgeBeforeDate = new Date(purgeBeforeTime);

            // Create condition for completed non-recurring tasks
            Condition purgeCondition = new Condition(PROPERTY_CONDITION_TYPE);
            purgeCondition.setParameter("propertyName", "status");
            purgeCondition.setParameter("comparisonOperator", "equals");
            purgeCondition.setParameter("propertyValue", TaskStatus.COMPLETED);

            Condition dateCondition = new Condition(PROPERTY_CONDITION_TYPE);
            dateCondition.setParameter("propertyName", "lastExecutionDate");
            dateCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            dateCondition.setParameter("propertyValueDate", purgeBeforeDate);

            Condition nonRecurringCondition = new Condition(PROPERTY_CONDITION_TYPE);
            nonRecurringCondition.setParameter("propertyName", "period");
            nonRecurringCondition.setParameter("comparisonOperator", "equals");
            nonRecurringCondition.setParameter("propertyValueInteger", 0);

            Condition andCondition = new Condition(BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(purgeCondition, dateCondition, nonRecurringCondition));

            // Remove tasks matching the condition
            persistenceService.removeByQuery(andCondition, ScheduledTask.class);
            LOGGER.info("Completed purge of old tasks before date: {}", purgeBeforeDate);
        } catch (Exception e) {
            LOGGER.error("Error purging old tasks", e);
        }
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPurgeTaskEnabled(boolean purgeTaskEnabled) {
        this.purgeTaskEnabled = purgeTaskEnabled;
    }

    public boolean isPurgeTaskEnabled() {
        return purgeTaskEnabled;
    }
}
