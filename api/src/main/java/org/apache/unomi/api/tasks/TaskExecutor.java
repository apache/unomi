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
 * Interface for task executors that can execute scheduled tasks.
 * Task executors are responsible for the actual execution of tasks and provide:
 * <ul>
 *   <li>Task type identification</li>
 *   <li>Task execution logic</li>
 *   <li>Optional task resumption capabilities</li>
 *   <li>Progress and status reporting through callbacks</li>
 * </ul>
 * 
 * Implementations should be thread-safe as they may be called concurrently
 * from multiple threads to execute different tasks of the same type.
 */
public interface TaskExecutor {
    
    /**
     * Gets the type of tasks this executor can handle.
     * The task type is used to match tasks with their appropriate executor.
     * Each executor must have a unique task type.
     * 
     * @return the task type string identifier
     */
    String getTaskType();

    /**
     * Executes a scheduled task.
     * This method contains the core execution logic for the task.
     * The implementation should:
     * <ul>
     *   <li>Use the task parameters to perform the required work</li>
     *   <li>Report progress through the status callback</li>
     *   <li>Handle errors appropriately</li>
     *   <li>Call callback.complete() on successful completion</li>
     *   <li>Call callback.fail() if execution fails</li>
     * </ul>
     * 
     * @param task the task to execute
     * @param statusCallback callback to update task status during execution
     * @throws Exception if task execution fails
     */
    void execute(ScheduledTask task, TaskStatusCallback statusCallback) throws Exception;

    /**
     * Checks if this executor can resume a crashed task from its checkpoint.
     * Implementations should examine the task's checkpoint data to determine
     * if resumption is possible.
     * 
     * @param task the crashed task
     * @return true if the task can be resumed from its checkpoint
     */
    default boolean canResume(ScheduledTask task) {
        return false;
    }

    /**
     * Resumes a crashed task from its checkpoint.
     * This method is called instead of execute() when resuming a crashed task.
     * The default implementation simply calls execute(), but implementations
     * can override this to provide custom resumption logic.
     * 
     * @param task the crashed task
     * @param statusCallback callback to update task status
     * @throws Exception if task resumption fails
     */
    default void resume(ScheduledTask task, TaskStatusCallback statusCallback) throws Exception {
        execute(task, statusCallback);
    }

    /**
     * Callback interface for task status updates.
     * This interface allows executors to report progress and status changes
     * during task execution.
     */
    interface TaskStatusCallback {
        /**
         * Updates the current step of the task.
         * Use this to indicate progress through different phases of execution.
         * 
         * @param step the current step name
         * @param details optional step details as key-value pairs
         */
        void updateStep(String step, Map<String, Object> details);

        /**
         * Saves a checkpoint for the task.
         * Checkpoints allow long-running tasks to be resumed after crashes.
         * The checkpoint data should contain sufficient information to
         * resume execution from this point.
         * 
         * @param checkpointData the checkpoint data as key-value pairs
         */
        void checkpoint(Map<String, Object> checkpointData);

        /**
         * Updates task status details.
         * Use this to provide additional information about the task's
         * current state or progress.
         * 
         * @param details the status details as key-value pairs
         */
        void updateStatusDetails(Map<String, Object> details);

        /**
         * Marks task as completed.
         * This should be called when the task has successfully finished
         * all its work.
         */
        void complete();

        /**
         * Marks task as failed.
         * This should be called when the task encounters an error that
         * prevents successful completion.
         * 
         * @param error the error message describing the failure
         */
        void fail(String error);
    }
} 
