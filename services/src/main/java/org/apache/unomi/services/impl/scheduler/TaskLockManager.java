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
import org.apache.unomi.api.conditions.ConditionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages task locks to coordinate execution in a cluster environment.
 * Ensures that tasks which do not allow parallel execution
 * only run on a single node at a time.
 *
 * <p>Distributed Locking Strategy:</p>
 *
 * <p>This implementation addresses the challenge of reliable distributed locking
 * with Elasticsearch, which is an eventually consistent system. The primary goal
 * is to ensure that only one node in the cluster acquires a lock at any time,
 * even if multiple nodes attempt to acquire it simultaneously.</p>
 *
 * <p>Key features of the locking implementation:</p>
 * <ul>
 *   <li><b>Node Affinity</b>: Each task is assigned a primary node based on its ID hash,
 *       reducing contention by giving priority to specific nodes for specific tasks.
 *       Active nodes are detected using the ClusterService and fall back to task lock analysis
 *       if ClusterService is unavailable.</li>
 *   <li><b>Time Windows</b>: Primary nodes get an exclusive time window to acquire locks,
 *       after which backup nodes attempt in sequence.</li>
 *   <li><b>Optimistic Concurrency Control</b>: Uses Elasticsearch's sequence numbers and
 *       primary terms to ensure only one update succeeds when multiple nodes attempt
 *       simultaneous updates.</li>
 *   <li><b>Fencing Tokens</b>: Monotonically increasing version numbers prevent split-brain
 *       scenarios where multiple nodes believe they own a lock.</li>
 *   <li><b>Lock Verification</b>: Double-checking after acquiring a lock ensures it's
 *       still valid after changes have propagated through the cluster.</li>
 *   <li><b>Explicit Refreshes</b>: Forces immediate index refreshes to make lock
 *       information visible more quickly to other nodes.</li>
 * </ul>
 *
 * <p>Different strategies are used for different task types:</p>
 * <ul>
 *   <li>Tasks that allow parallel execution: Simple locking without exclusivity</li>
 *   <li>Non-persistent tasks: Simple in-memory locking (these exist only on one node)</li>
 *   <li>Persistent tasks: Robust distributed locking with all safeguards</li>
 * </ul>
 */
public class TaskLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLockManager.class);
    private static final String SEQ_NO = "seq_no";
    private static final String PRIMARY_TERM = "primary_term";
    private static final String LOCK_VERSION = "lockVersion";
    private static final long VERIFICATION_DELAY_MS = 100;
    private static final long PRIMARY_NODE_WINDOW_MS = 3000;
    private static final long BACKUP_NODE_WINDOW_MS = 500;

    private String nodeId;
    private long lockTimeout;
    private TaskMetricsManager metricsManager;
    private SchedulerServiceImpl schedulerService;
    /** Per-task guards for in-memory exclusive acquire (shared across manager instances). */
    private static final ConcurrentHashMap<String, Object> IN_MEMORY_LOCK_GUARDS = new ConcurrentHashMap<>();

    /**
     * Creates the manager for Blueprint dependency injection.
     */
    public TaskLockManager() {
        // Parameterless constructor for Blueprint dependency injection
    }

    /**
     * Sets the cluster node ID.
     *
     * @param nodeId the node ID
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * Sets the lock timeout in milliseconds.
     *
     * @param lockTimeout lock expiry timeout
     */
    public void setLockTimeout(long lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    /**
     * Returns the lock expiry timeout in milliseconds.
     *
     * @return the lock timeout
     */
    public long getLockTimeout() {
        return lockTimeout;
    }

    /**
     * Sets the task metrics manager.
     *
     * @param metricsManager the metrics manager
     */
    public void setMetricsManager(TaskMetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    /**
     * Sets the scheduler service reference.
     *
     * @param schedulerService the scheduler service
     */
    public void setSchedulerService(SchedulerServiceImpl schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Acquires a lock for the specified task.
     * Uses optimistic concurrency control to ensure only one node successfully acquires a lock.
     *
     * Note: This implementation uses Elasticsearch/OpenSearch documents as distributed locks.
     * The refresh policy for ScheduledTask documents is configured to use WAIT_UNTIL/WaitFor
     * to ensure that lock changes are immediately visible to all nodes without requiring
     * explicit refresh calls.
     *
     * @param task The task to lock
     * @return true if the lock was successfully acquired, false otherwise
     */
    public boolean acquireLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        // Always allow tasks that permit parallel execution
        if (task.isAllowParallelExecution()) {
            // Just set lock info but don't enforce exclusivity
            task.setLockOwner(nodeId);
            task.setLockDate(new Date());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
            return true;
        }

        // For non-persistent tasks, use simple in-memory locking
        if (!task.isPersistent()) {
            return acquireInMemoryLock(task);
        }

        // For persistent tasks, use robust distributed locking
        return acquireDistributedLock(task);
    }

    /**
     * Simple in-memory locking for non-persistent tasks.
     * These tasks exist only on a single node, so we don't need
     * complex distributed locking.
     */
    private boolean acquireInMemoryLock(ScheduledTask task) {
        Object guard = IN_MEMORY_LOCK_GUARDS.computeIfAbsent(task.getItemId(), id -> new Object());
        synchronized (guard) {
            // Re-load the node-local task so concurrent acquires see each other's lock writes.
            ScheduledTask latest = schedulerService.getTask(task.getItemId());
            if (latest == null) {
                latest = task;
            }
            if (latest.getLockOwner() != null && !nodeId.equals(latest.getLockOwner())
                    && !isLockExpired(latest)) {
                return false;
            }

            latest.setLockOwner(nodeId);
            latest.setLockDate(new Date());
            task.setLockOwner(nodeId);
            task.setLockDate(latest.getLockDate());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);

            // For non-persistent tasks, we just update the in-memory map
            schedulerService.saveTask(latest);
            return true;
        }
    }

    /**
     * Robust distributed locking for persistent tasks.
     * This handles the case where multiple nodes might try to
     * acquire the lock at the same time.
     */
    private boolean acquireDistributedLock(ScheduledTask task) {
        // Step 1: Check if this node should handle this task based on affinity
        if (!shouldHandleTask(task)) {
            return false;
        }

        // Step 2: Force a refresh to ensure we see the latest state
        schedulerService.refreshTasks();

        // Step 3: Get the latest version using GET by ID (not search)
        ScheduledTask latestTask = schedulerService.getTask(task.getItemId());
        if (latestTask == null) {
            LOGGER.warn("Task {} not found when attempting to lock", task.getItemId());
            return false;
        }

        // Step 4: Check if already locked by another node
        if (latestTask.getLockOwner() != null &&
            !nodeId.equals(latestTask.getLockOwner()) &&
            !isLockExpired(latestTask)) {
            LOGGER.debug("Task {} already locked by {}", task.getItemId(), latestTask.getLockOwner());
            return false;
        }

        // Step 5: Use optimistic concurrency control with sequence numbers
        task.setSystemMetadata(SEQ_NO, latestTask.getSystemMetadata(SEQ_NO));
        task.setSystemMetadata(PRIMARY_TERM, latestTask.getSystemMetadata(PRIMARY_TERM));

        // Step 6: Set lock information
        task.setLockOwner(nodeId);
        task.setLockDate(new Date());

        // Step 7: Add a monotonically increasing fencing token
        Object lockVersionObj = latestTask.getSystemMetadata(LOCK_VERSION);
        long newLockVersion = (lockVersionObj instanceof Number)
            ? ((Number) lockVersionObj).longValue() + 1L
            : 1L;
        task.setSystemMetadata(LOCK_VERSION, newLockVersion);

        // Step 8: Save with WAIT_UNTIL refresh policy
        boolean acquired = schedulerService.saveTaskWithRefresh(task);

        if (!acquired) {
            LOGGER.debug("Failed to acquire lock for task {} due to version conflict", task.getItemId());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            return false;
        }

        // Step 9: Double-check our lock after a delay to ensure it's still valid
        try {
            // Wait for a short time to allow any concurrent operations to complete
            Thread.sleep(VERIFICATION_DELAY_MS);

            // Force refresh again to ensure we see the latest state
            schedulerService.refreshTasks();

            // Get the task again to verify our lock
            ScheduledTask verifiedTask = schedulerService.getTask(task.getItemId());
            if (isLockVerificationSuccessful(verifiedTask, newLockVersion)) {
                LOGGER.debug("Successfully acquired and verified lock for task {}", task.getItemId());
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
                return true;
            }

            // One more GET to absorb brief ES/OS visibility false-negatives after a successful CAS.
            schedulerService.refreshTasks();
            ScheduledTask recheck = schedulerService.getTask(task.getItemId());
            if (isLockVerificationSuccessful(recheck, newLockVersion)) {
                LOGGER.debug("Lock for task {} verified on recheck after transient visibility miss",
                    task.getItemId());
                metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
                return true;
            }

            LOGGER.warn("Lock verification failed for task {} after CAS; releasing if still owned",
                task.getItemId());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            // Release only when we still own — never wipe a peer that won the race.
            if (recheck != null && nodeId.equals(recheck.getLockOwner())) {
                releaseLock(task);
            } else if (verifiedTask != null && nodeId.equals(verifiedTask.getLockOwner())) {
                releaseLock(task);
            } else if (recheck == null && verifiedTask == null) {
                // Document vanished; best-effort clear local marker
                releaseLock(task);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Attempt to release the lock since we're being interrupted
            releaseLock(task);
            return false;
        }
    }

    private boolean isLockVerificationSuccessful(ScheduledTask verifiedTask, long expectedLockVersion) {
        if (verifiedTask == null || !nodeId.equals(verifiedTask.getLockOwner())) {
            return false;
        }
        Object currentTokenObj = verifiedTask.getSystemMetadata(LOCK_VERSION);
        long currentToken = (currentTokenObj instanceof Number)
            ? ((Number) currentTokenObj).longValue()
            : -1L;
        return currentToken == expectedLockVersion;
    }

    /**
     * Determines if this node should handle the given task based on node affinity.
     * This reduces contention by giving priority to a specific node for each task.
     */
    private boolean shouldHandleTask(ScheduledTask task) {
        // Crash recovery has already chosen this node to resume/restart. Affinity windows
        // must not block that path — a dead primary often remains in getActiveNodes() long
        // enough that the backup would otherwise wait out PRIMARY_NODE_WINDOW_MS and miss
        // the immediate recoverCrashedTasks() dispatch (checkpoint resume tests / failover).
        if (task.getStatus() == ScheduledTask.TaskStatus.CRASHED) {
            return true;
        }

        // Check if this is a scheduled task
        Date scheduledTime = task.getNextScheduledExecution();
        if (scheduledTime == null) {
            // Not a scheduled task, any node can handle it
            return true;
        }

        // Get list of active nodes (sorted for consistency)
        List<String> activeNodes = schedulerService.getActiveNodes();
        if (activeNodes.isEmpty() || activeNodes.size() == 1) {
            // If we're the only node or can't determine active nodes, always handle the task
            return true;
        }
        Collections.sort(activeNodes);

        // Calculate primary node based on task hash
        int primaryIndex = Math.abs(task.getItemId().hashCode() % activeNodes.size());
        String primaryNode = activeNodes.get(primaryIndex);

        // If we're the primary node, always attempt
        if (nodeId.equals(primaryNode)) {
            return true;
        }

        // Check if enough time has passed to allow backup nodes
        long delayMs = System.currentTimeMillis() - scheduledTime.getTime();

        // Primary node gets exclusive window
        if (delayMs < PRIMARY_NODE_WINDOW_MS) {
            return false;
        }

        // Calculate our position as a backup node
        int ourIndex = activeNodes.indexOf(nodeId);
        if (ourIndex < 0) {
            return false; // Not in active nodes list
        }

        // Calculate backup order (relative position after primary)
        int backupOrder = (ourIndex - primaryIndex + activeNodes.size()) % activeNodes.size();

        // Each backup node gets a staggered time window based on their order to reduce
        // contention during normal operation. After every backup has had a window, open the
        // field to any active node — otherwise a dead primary that remains in getActiveNodes()
        // would permanently strand the task (backups would return false forever after their
        // short 500ms slots closed).
        int backupCount = activeNodes.size() - 1;
        long openFieldStart = PRIMARY_NODE_WINDOW_MS + (backupCount * BACKUP_NODE_WINDOW_MS);
        if (delayMs >= openFieldStart) {
            return true;
        }

        long ourWindowStart = PRIMARY_NODE_WINDOW_MS + ((backupOrder - 1) * BACKUP_NODE_WINDOW_MS);
        long ourWindowEnd = ourWindowStart + BACKUP_NODE_WINDOW_MS;

        return delayMs >= ourWindowStart && delayMs < ourWindowEnd;
    }

    /**
     * Releases a lock on the given task.
     *
     * @param task Task to unlock
     * @return true if unlock was successful
     */
    public boolean releaseLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        // Fast reject from the caller's view: only the lock owner may release a still-valid lock.
        // Expired locks may be cleared by any recovering node so a dead owner's lock does not
        // block failover — but that decision is re-validated against the fresh store view below.
        if (task.getLockOwner() != null && !nodeId.equals(task.getLockOwner()) && !isLockExpired(task)) {
            LOGGER.warn("Node {} attempted to release a lock owned by {}", nodeId, task.getLockOwner());
            return false;
        }

        try {
            // Clear lock on a freshly loaded copy when possible. Callers such as
            // handleTaskError() may have already mutated the in-memory task to
            // SCHEDULED/FAILED for retry; persisting that object during shutdown
            // would hide the RUNNING/CRASHED state peers need for recovery.
            ScheduledTask toSave = task;
            ScheduledTask latest = schedulerService.getTask(task.getItemId(), true);
            if (latest != null) {
                toSave = latest;
            }

            // Re-validate against the store: a peer may have stolen the lock after our local
            // view expired. Never wipe a non-expired foreign lock (that would unlock a live
            // peer mid-execution and enable double-dispatch).
            String latestOwner = toSave.getLockOwner();
            if (latestOwner == null) {
                task.setLockOwner(null);
                task.setLockDate(null);
                return true;
            }
            boolean weOwnLatest = nodeId.equals(latestOwner);
            boolean latestExpired = isLockExpired(toSave);
            if (!weOwnLatest && !latestExpired) {
                LOGGER.warn(
                    "Node {} not releasing task {}: store lock is owned by {} and has not expired",
                    nodeId, task.getItemId(), latestOwner);
                return false;
            }

            toSave.setLockOwner(null);
            toSave.setLockDate(null);
            task.setLockOwner(null);
            task.setLockDate(null);

            // Compare-and-set on the freshly loaded seq_no/primary_term, not a blind overwrite:
            // a peer may win a legitimate CAS-based lock acquisition in the window between our
            // read above and this write. A blind overwrite would silently clobber that peer's
            // new lock; CAS instead fails closed and we report a lost race below. Allow persist
            // during shutdown: preDestroy/simulateCrash set shutdownNow before releasing locks,
            // and a no-op save would leave a stale lock in deep-copy stores.
            if (!schedulerService.saveTaskWithRefresh(toSave, true)) {
                LOGGER.warn("Failed to release lock for task {}: lost compare-and-set race, "
                        + "a peer likely re-acquired the lock", task.getItemId());
                return false;
            }

            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error releasing lock for task {}", task.getItemId(), e);
            return false;
        }
    }

    /**
     * Renews (heartbeats) a held distributed lock by refreshing its {@code lockDate}, so that
     * expiry only ever means "the owner stopped renewing" (crashed or unreachable), never
     * merely "the execution outlived the timeout". A live owner that keeps renewing never
     * looks expired to peers, which closes the window where crash recovery could steal the
     * lock from a node that is still genuinely executing and double-run the task.
     *
     * Only persistent exclusive tasks need renewal: parallel-execution and non-persistent
     * tasks return true without touching the store. Renewal deliberately uses the
     * shutdown-sensitive load/save paths — once shutdown begins the lock must be allowed to
     * age out so peers can recover the work.
     *
     * @param task the executing task whose lock should be renewed (caller must own the lock)
     * @return true if the lock was renewed (or renewal is not applicable), false if ownership
     *         was lost or the compare-and-set write failed (benign: a peer took over)
     */
    public boolean renewLock(ScheduledTask task) {
        if (task == null) {
            return false;
        }
        if (!task.isPersistent() || task.isAllowParallelExecution()) {
            return true;
        }
        if (!nodeId.equals(task.getLockOwner())) {
            return false;
        }
        try {
            ScheduledTask latest = schedulerService.getTask(task.getItemId());
            if (latest == null || !nodeId.equals(latest.getLockOwner())) {
                LOGGER.debug("Not renewing lock for task {}: store owner is {}",
                    task.getItemId(), latest != null ? latest.getLockOwner() : null);
                return false;
            }

            latest.setLockDate(new Date());

            // Compare-and-set on the fresh store view: if a peer stole the lock between the
            // read above and this write, renewal fails closed instead of resurrecting our lock.
            if (!schedulerService.saveTaskWithRefresh(latest)) {
                LOGGER.debug("Lock renewal for task {} lost a compare-and-set race", task.getItemId());
                return false;
            }

            // Sync the renewed date and post-save OCC tokens back onto the caller's task so
            // the executing thread's later compare-and-set writes are checked against the
            // store's current version, not the pre-renewal one.
            task.setLockDate(latest.getLockDate());
            copyOccMetadata(latest, task);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Error renewing lock for task {}", task.getItemId(), e);
            return false;
        }
    }

    /**
     * Copies OCC (seq_no/primary_term) fencing metadata from a freshly loaded task onto the
     * task about to be persisted, so a subsequent compare-and-set save is checked against the
     * store's current version rather than a stale in-memory one. {@code from} and {@code to}
     * may be the same instance (normalizes both ES/OS key variants onto it).
     *
     * @param from the task holding the current OCC metadata (typically a fresh store read)
     * @param to the task that will be persisted next
     */
    public static void copyOccMetadata(ScheduledTask from, ScheduledTask to) {
        Object seq = from.getSystemMetadata(SEQ_NO);
        if (seq == null) {
            seq = from.getSystemMetadata("_seq_no");
        }
        Object term = from.getSystemMetadata(PRIMARY_TERM);
        if (term == null) {
            term = from.getSystemMetadata("_primary_term");
        }
        if (seq != null) {
            to.setSystemMetadata(SEQ_NO, seq);
            to.setSystemMetadata("_seq_no", seq);
        }
        if (term != null) {
            to.setSystemMetadata(PRIMARY_TERM, term);
            to.setSystemMetadata("_primary_term", term);
        }
    }

    /**
     * Checks if a task's lock has expired based on timeout.
     *
     * @param task Task to check
     * @return true if lock has expired or if task has no lock
     */
    public boolean isLockExpired(ScheduledTask task) {
        if (task == null || task.getLockDate() == null) {
            return true;
        }

        long lockAge = System.currentTimeMillis() - task.getLockDate().getTime();
        return lockAge > lockTimeout;
    }
}
