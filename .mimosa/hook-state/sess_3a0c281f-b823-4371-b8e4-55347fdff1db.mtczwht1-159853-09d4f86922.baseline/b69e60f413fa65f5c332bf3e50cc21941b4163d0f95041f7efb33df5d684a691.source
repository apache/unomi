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

import org.apache.unomi.api.tasks.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for task executors shared between scheduler providers.
 * 
 * This registry manages the task executors that are available to all providers.
 * It provides thread-safe registration and lookup of executors by task type.
 * 
 * The registry is shared between providers so that task executors registered
 * with the scheduler service are available to both memory and persistence providers.
 */
public class TaskExecutorRegistry {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutorRegistry.class);
    
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    
    /**
     * Registers a task executor for a specific task type.
     * 
     * @param executor the task executor to register
     * @throws IllegalArgumentException if executor is null or task type is null/empty
     */
    public void registerExecutor(TaskExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("TaskExecutor cannot be null");
        }
        
        String taskType = executor.getTaskType();
        if (taskType == null || taskType.trim().isEmpty()) {
            throw new IllegalArgumentException("Task type cannot be null or empty");
        }
        
        TaskExecutor previous = executors.put(taskType, executor);
        if (previous != null) {
            LOGGER.warn("Replaced existing executor for task type: {}", taskType);
        }
        
        LOGGER.debug("Registered executor for task type: {}", taskType);
    }
    
    /**
     * Unregisters a task executor.
     * 
     * @param executor the task executor to unregister
     */
    public void unregisterExecutor(TaskExecutor executor) {
        if (executor == null) {
            return;
        }
        
        String taskType = executor.getTaskType();
        if (taskType == null) {
            return;
        }
        
        TaskExecutor removed = executors.remove(taskType);
        if (removed != null) {
            LOGGER.debug("Unregistered executor for task type: {}", taskType);
        }
    }
    
    /**
     * Gets the task executor for a specific task type.
     * 
     * @param taskType the task type
     * @return the task executor, or null if not found
     */
    public TaskExecutor getExecutor(String taskType) {
        if (taskType == null) {
            return null;
        }
        
        return executors.get(taskType);
    }
    
    /**
     * Checks if an executor is registered for the given task type.
     * 
     * @param taskType the task type
     * @return true if an executor is registered
     */
    public boolean hasExecutor(String taskType) {
        return taskType != null && executors.containsKey(taskType);
    }
    
    /**
     * Gets all registered task types.
     * 
     * @return set of all registered task types
     */
    public Set<String> getRegisteredTaskTypes() {
        return Collections.unmodifiableSet(executors.keySet());
    }
    
    /**
     * Gets the number of registered executors.
     * 
     * @return the number of registered executors
     */
    public int getExecutorCount() {
        return executors.size();
    }
    
    /**
     * Clears all registered executors.
     * This is typically used during shutdown.
     */
    public void clear() {
        int count = executors.size();
        executors.clear();
        LOGGER.debug("Cleared {} registered executors", count);
    }
    
    /**
     * Gets an unmodifiable view of all registered executors.
     * 
     * @return map of task type to executor
     */
    public Map<String, TaskExecutor> getAllExecutors() {
        return Collections.unmodifiableMap(executors);
    }
} 