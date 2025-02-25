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

import org.apache.unomi.api.tasks.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages task scheduling, including periodic tasks, one-shot tasks,
 * and scheduling policies (fixed-rate vs fixed-delay).
 */
public class TaskSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskSchedulingManager.class);
    private static final int MIN_THREAD_POOL_SIZE = 4;

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;
    private final TaskMetricsManager metricsManager;

    public TaskSchedulingManager(int threadPoolSize, TaskMetricsManager metricsManager) {
        this.metricsManager = metricsManager;
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(
            Math.max(MIN_THREAD_POOL_SIZE, threadPoolSize),
            r -> {
                Thread t = new Thread(r);
                t.setName("UnomiScheduler-" + t.getId());
                t.setDaemon(true);
                return t;
            }
        );
    }

    /**
     * Schedules a task for execution based on its configuration
     */
    public void scheduleTask(ScheduledTask task, Runnable taskRunner) {
        if (task.getPeriod() > 0 && !task.isOneShot()) {
            schedulePeriodicTask(task, taskRunner);
        } else {
            scheduleOneTimeTask(task, taskRunner);
        }
    }

    private void schedulePeriodicTask(ScheduledTask task, Runnable taskRunner) {
        ScheduledFuture<?> future;
        if (task.isFixedRate()) {
            future = scheduler.scheduleAtFixedRate(
                taskRunner,
                task.getInitialDelay(),
                task.getPeriod(),
                task.getTimeUnit()
            );
        } else {
            future = scheduler.scheduleWithFixedDelay(
                taskRunner,
                task.getInitialDelay(),
                task.getPeriod(),
                task.getTimeUnit()
            );
        }
        scheduledTasks.put(task.getItemId(), future);
    }

    private void scheduleOneTimeTask(ScheduledTask task, Runnable taskRunner) {
        if (task.getInitialDelay() > 0) {
            ScheduledFuture<?> future = scheduler.schedule(
                taskRunner,
                task.getInitialDelay(),
                task.getTimeUnit()
            );
            scheduledTasks.put(task.getItemId(), future);
        } else {
            Future<?> future = scheduler.submit(taskRunner);
            scheduledTasks.put(task.getItemId(), createDummyFuture());
        }
    }

    /**
     * Calculates the next execution time for a periodic task
     */
    public void updateNextExecutionTime(ScheduledTask task) {
        if (task.isOneShot() || task.getPeriod() == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextExecutionTime;

        if (task.isFixedRate()) {
            nextExecutionTime = calculateNextFixedRateExecution(task, now);
        } else {
            nextExecutionTime = now + task.getTimeUnit().toMillis(task.getPeriod());
        }

        task.setNextScheduledExecution(new Date(nextExecutionTime));
    }

    private long calculateNextFixedRateExecution(ScheduledTask task, long now) {
        long lastScheduledTime = task.getNextScheduledExecution() != null ?
            task.getNextScheduledExecution().getTime() :
            (task.getLastExecutionDate() != null ? task.getLastExecutionDate().getTime() : now);
        long nextExecutionTime = lastScheduledTime + task.getTimeUnit().toMillis(task.getPeriod());

        // If we're behind schedule, move to the next interval
        while (nextExecutionTime <= now) {
            nextExecutionTime += task.getTimeUnit().toMillis(task.getPeriod());
        }

        return nextExecutionTime;
    }

    /**
     * Cancels a scheduled task
     */
    public void cancelTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Shuts down the scheduler
     */
    public void shutdown() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(true);
        }
        scheduledTasks.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    private ScheduledFuture<?> createDummyFuture() {
        return new ScheduledFuture<Object>() {
            @Override
            public long getDelay(TimeUnit unit) { return 0; }
            @Override
            public int compareTo(Delayed o) { return 0; }
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) { return true; }
            @Override
            public boolean isCancelled() { return false; }
            @Override
            public boolean isDone() { return true; }
            @Override
            public Object get() { return null; }
            @Override
            public Object get(long timeout, TimeUnit unit) { return null; }
        };
    }
} 