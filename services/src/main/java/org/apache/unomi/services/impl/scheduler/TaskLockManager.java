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
import org.apache.unomi.persistence.spi.PersistenceService;
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
 *   <li><b>Optimistic Concurrency Control</b>: Uses the persistence backend's native sequence
 *       numbers and primary terms ({@link PersistenceService#SYSTEM_METADATA_SEQ_NO}/
 *       {@link PersistenceService#SYSTEM_METADATA_PRIMARY_TERM}) as the compare-and-set
 *       precondition, via {@code PersistenceService#save(Item, Boolean, Boolean)}. This is backend-agnostic:
 *       any persistence implementation that honors that CAS contract (both the Elasticsearch
 *       and OpenSearch backends do) works here without scheduler-specific changes.</li>
 *   <li><b>Fencing Tokens</b>: No separate application-level version counter is maintained.
 *       The backend's own seq_no/primary_term pair already changes atomically on every
 *       successful write and serves directly as the fencing token — a successful CAS write
 *       is itself authoritative proof of exclusive acquisition, so no post-write re-read is
 *       performed. (An earlier version of this class re-verified acquisition with a delayed
 *       re-read compared against a custom "lockVersion" field; that added a race window of
 *       its own — a concurrent renewal/recovery write landing between the CAS and the re-read
 *       could flip the comparison and produce a false "lost the lock" verdict for an
 *       acquisition that had, in fact, already succeeded — without adding any real safety
 *       over trusting the CAS result directly, which {@link #renewLock} and {@link #releaseLock}
 *       always have.)</li>
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
    private static final String SEQ_NO = PersistenceService.SYSTEM_METADATA_SEQ_NO;
    private static final String PRIMARY_TERM = PersistenceService.SYSTEM_METADATA_PRIMARY_TERM;
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
            task.setLockLeaseMillis(lockTimeout);
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
            latest.setLockLeaseMillis(lockTimeout);
            task.setLockOwner(nodeId);
            task.setLockDate(latest.getLockDate());
            task.setLockLeaseMillis(lockTimeout);
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
        long diagStart = System.currentTimeMillis();
        // Step 1: Check if this node should handle this task based on affinity
        if (!shouldHandleTask(task)) {
            LOGGER.debug("LOCK-DIAG [{}] node {} : shouldHandleTask()=false, not attempting acquisition",
                task.getItemId(), nodeId);
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
        LOGGER.debug("LOCK-DIAG [{}] node {} : pre-CAS read - lockOwner={}, lockDate={}, seq_no={}, "
                + "primary_term={}, status={}",
            task.getItemId(), nodeId, latestTask.getLockOwner(), latestTask.getLockDate(),
            latestTask.getSystemMetadata(SEQ_NO), latestTask.getSystemMetadata(PRIMARY_TERM), latestTask.getStatus());

        // Step 4: Check if already locked by another node
        if (latestTask.getLockOwner() != null &&
            !nodeId.equals(latestTask.getLockOwner()) &&
            !isLockExpired(latestTask)) {
            LOGGER.debug("Task {} already locked by {}", task.getItemId(), latestTask.getLockOwner());
            return false;
        }

        // Step 5: Use optimistic concurrency control with sequence numbers as the CAS
        // precondition. The backend rejects this write outright unless seq_no/primary_term
        // still match what we just read, so a successful write below is itself authoritative
        // proof of exclusive acquisition - no separate re-read-and-compare step is needed.
        task.setSystemMetadata(SEQ_NO, latestTask.getSystemMetadata(SEQ_NO));
        task.setSystemMetadata(PRIMARY_TERM, latestTask.getSystemMetadata(PRIMARY_TERM));

        // Step 6: Set lock information. The lease records THIS node's timeout with the lock:
        // renewal cadence is derived from the owner's timeout, so only the owner's timeout says
        // when a missing renewal means the owner is dead (see isLockExpired()).
        task.setLockOwner(nodeId);
        task.setLockDate(new Date());
        task.setLockLeaseMillis(lockTimeout);

        LOGGER.debug("LOCK-DIAG [{}] node {} : attempting CAS write - if_seq_no={}, if_primary_term={}, "
                + "writing lockOwner={}",
            task.getItemId(), nodeId, task.getSystemMetadata(SEQ_NO), task.getSystemMetadata(PRIMARY_TERM), nodeId);

        // Step 7: Save with WAIT_UNTIL refresh policy. The backend's own CAS result is
        // authoritative: true means our precondition matched and the write applied atomically,
        // so we now exclusively hold the lock. task's seq_no/primary_term are updated in place
        // to the post-write values, which double as an opaque fencing token for this generation
        // of the lock (see PersistenceService#SYSTEM_METADATA_SEQ_NO).
        boolean acquired = schedulerService.saveTaskWithRefresh(task);

        LOGGER.debug("LOCK-DIAG [{}] node {} : CAS write result acquired={} in {} ms, post-write seq_no={}, "
                + "post-write primary_term={}",
            task.getItemId(), nodeId, acquired, System.currentTimeMillis() - diagStart,
            task.getSystemMetadata(SEQ_NO), task.getSystemMetadata(PRIMARY_TERM));

        if (!acquired) {
            LOGGER.debug("Failed to acquire lock for task {} due to version conflict", task.getItemId());
            metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS);
            return false;
        }

        metricsManager.updateMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED);
        return true;
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
        LOGGER.debug("LOCK-DIAG [{}] node {} : releaseLock() called - caller's view lockOwner={}, lockDate={}",
            task.getItemId(), nodeId, task.getLockOwner(), task.getLockDate());

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
            LOGGER.debug("LOCK-DIAG [{}] node {} : releaseLock() fresh store read - lockOwner={}, "
                    + "lockDate={}, seq_no={}, primary_term={}",
                task.getItemId(), nodeId, latestOwner, toSave.getLockDate(),
                toSave.getSystemMetadata(SEQ_NO), toSave.getSystemMetadata(PRIMARY_TERM));
            if (latestOwner == null) {
                task.setLockOwner(null);
                task.setLockDate(null);
                task.setLockLeaseMillis(0);
                LOGGER.debug("LOCK-DIAG [{}] node {} : releaseLock() no-op, store already unlocked",
                    task.getItemId(), nodeId);
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
            toSave.setLockLeaseMillis(0);
            task.setLockOwner(null);
            task.setLockDate(null);
            task.setLockLeaseMillis(0);

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

            LOGGER.debug("LOCK-DIAG [{}] node {} : releaseLock() CAS write succeeded, lock cleared",
                task.getItemId(), nodeId);
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
            LOGGER.debug("LOCK-DIAG [{}] node {} : renewLock() skipped - caller's view lockOwner={} != nodeId",
                task.getItemId(), nodeId, task.getLockOwner());
            return false;
        }
        try {
            ScheduledTask latest = schedulerService.getTask(task.getItemId());
            LOGGER.debug("LOCK-DIAG [{}] node {} : renewLock() fresh store read - lockOwner={}, lockDate={}, "
                    + "seq_no={}, primary_term={}",
                task.getItemId(), nodeId, latest != null ? latest.getLockOwner() : "<null-task>",
                latest != null ? latest.getLockDate() : null,
                latest != null ? latest.getSystemMetadata(SEQ_NO) : null,
                latest != null ? latest.getSystemMetadata(PRIMARY_TERM) : null);
            if (latest == null || !nodeId.equals(latest.getLockOwner())) {
                LOGGER.debug("Not renewing lock for task {}: store owner is {}",
                    task.getItemId(), latest != null ? latest.getLockOwner() : null);
                return false;
            }

            latest.setLockDate(new Date());
            latest.setLockLeaseMillis(lockTimeout);

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
            task.setLockLeaseMillis(latest.getLockLeaseMillis());
            copyOccMetadata(latest, task);
            LOGGER.debug("LOCK-DIAG [{}] node {} : renewLock() succeeded, new lockDate={}",
                task.getItemId(), nodeId, latest.getLockDate());
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
            LOGGER.debug("LOCK-DIAG isLockExpired() : task={}, lockDate=null -> expired=true",
                task != null ? task.getItemId() : "<null-task>");
            return true;
        }

        // Judge expiry against the lease the OWNER recorded with the lock, not this node's own
        // configured timeout. The owner renews on a cadence derived from its own timeout
        // (lockTimeout/3, see TaskExecutionManager#startLockRenewal), so a node configured with a
        // shorter timeout than the owner's renewal cadence would otherwise declare a live,
        // renewed lock dead in the gap between two renewals and "recover" a task that is still
        // executing — observed as double execution under divergent per-node configuration.
        // Locks written before lease recording carry no lease (0); only for those does this
        // node's own timeout remain the best available guess.
        long lease = task.getLockLeaseMillis() > 0 ? task.getLockLeaseMillis() : lockTimeout;
        long now = System.currentTimeMillis();
        long lockAge = now - task.getLockDate().getTime();
        boolean expired = lockAge > lease;
        LOGGER.debug("LOCK-DIAG isLockExpired() : task={}, lockDate={} ({}), now={}, lockAge={}ms, "
                + "lease={}ms (recorded={}ms, own timeout={}ms) -> expired={}",
            task.getItemId(), task.getLockDate(), task.getLockDate().getTime(), now, lockAge,
            lease, task.getLockLeaseMillis(), lockTimeout, expired);
        return expired;
    }
}
