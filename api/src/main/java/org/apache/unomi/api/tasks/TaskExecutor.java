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
package org.apache.unomi.api.tasks;

import java.util.Map;

/**
 * Interface for task executors that can execute scheduled tasks
 */
public interface TaskExecutor {
    
    /**
     * Get the type of tasks this executor can handle
     * @return the task type string
     */
    String getTaskType();

    /**
     * Execute a scheduled task
     * @param task the task to execute
     * @param statusCallback callback to update task status
     * @throws Exception if task execution fails
     */
    void execute(ScheduledTask task, TaskStatusCallback statusCallback) throws Exception;

    /**
     * Check if this executor can resume a crashed task from its checkpoint
     * @param task the crashed task
     * @return true if the task can be resumed
     */
    default boolean canResume(ScheduledTask task) {
        return false;
    }

    /**
     * Resume a crashed task from its checkpoint
     * @param task the crashed task
     * @param statusCallback callback to update task status
     * @throws Exception if task resumption fails
     */
    default void resume(ScheduledTask task, TaskStatusCallback statusCallback) throws Exception {
        execute(task, statusCallback);
    }

    /**
     * Callback interface for task status updates
     */
    interface TaskStatusCallback {
        /**
         * Update the current step of the task
         * @param step the current step name
         * @param details optional step details
         */
        void updateStep(String step, Map<String, Object> details);

        /**
         * Save a checkpoint for the task
         * @param checkpointData the checkpoint data
         */
        void checkpoint(Map<String, Object> checkpointData);

        /**
         * Update task status details
         * @param details the status details
         */
        void updateStatusDetails(Map<String, Object> details);

        /**
         * Mark task as completed
         */
        void complete();

        /**
         * Mark task as failed
         * @param error the error message
         */
        void fail(String error);
    }
} 