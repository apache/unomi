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
package org.apache.unomi.api.rules;

import org.apache.unomi.api.Item;

import java.util.Date;

/**
 * Persisted statistics for a {@link org.apache.unomi.api.rules.Rule}, stored separately from the rule
 * definition so counters can be updated without rewriting the rule itself.
 * <p>
 * Cluster-wide fields ({@link #executionCount}, {@link #conditionsTime}, {@link #actionsTime}) hold
 * aggregated totals synchronized across nodes. Matching {@code local*} fields track counts and
 * timings on the current node since {@link #lastSyncDate}; they are merged into the cluster totals
 * during synchronization.
 */
public class RuleStatistics extends Item {

    /**
     * The RuleStatistics ITEM_TYPE.
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "rulestats";
    private static final long serialVersionUID = 1L;

    /**
     * Cluster-wide execution count (excluding unsynchronized local count).
     * @api.example 42
     */
    private long executionCount = 0;
    /**
     * Executions on this node since {@link #lastSyncDate}.
     * @api.example 3
     */
    private long localExecutionCount = 0;
    /**
     * Cluster-wide time spent evaluating conditions, in milliseconds.
     * @api.example 120
     */
    private long conditionsTime = 0;
    /**
     * Condition evaluation time on this node since last sync, in milliseconds.
     * @api.example 8
     */
    private long localConditionsTime = 0;
    /**
     * Cluster-wide time spent running actions, in milliseconds.
     * @api.example 95
     */
    private long actionsTime = 0;
    /**
     * Action execution time on this node since last sync, in milliseconds.
     * @api.example 5
     */
    private long localActionsTime = 0;
    /**
     * When local counters were last merged into cluster totals (ISO-8601 in JSON).
     * @api.example 2024-06-15T11:00:00.000Z
     */
    private Date lastSyncDate;

    /**
     * Default constructor.
     */
    public RuleStatistics() {
    }

    /**
     * Creates statistics for the rule with the given identifier.
     *
     * @param itemId the rule item identifier these statistics belong to
     */
    public RuleStatistics(String itemId) {
        super(itemId);
    }

    /**
     * Cluster-wide execution count (excluding the current node's unsynchronized local count).
     *
     * @return the cluster execution count
     */
    public long getExecutionCount() {
        return executionCount;
    }

    /**
     * Sets the cluster-wide execution count.
     *
     * @param executionCount the cluster execution count
     */
    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    /**
     * Execution count on this node since the last cluster synchronization.
     *
     * @return the local execution count
     */
    public long getLocalExecutionCount() {
        return localExecutionCount;
    }

    /**
     * Sets the local execution count since the last cluster synchronization.
     *
     * @param localExecutionCount the local execution count
     */
    public void setLocalExecutionCount(long localExecutionCount) {
        this.localExecutionCount = localExecutionCount;
    }

    /**
     * Cluster-wide accumulated time spent evaluating rule conditions, in milliseconds.
     *
     * @return the cluster conditions time in milliseconds
     */
    public long getConditionsTime() {
        return conditionsTime;
    }

    /**
     * Sets the cluster-wide accumulated conditions evaluation time.
     *
     * @param conditionsTime the cluster conditions time in milliseconds
     */
    public void setConditionsTime(long conditionsTime) {
        this.conditionsTime = conditionsTime;
    }

    /**
     * Local accumulated time spent evaluating rule conditions since the last cluster sync, in milliseconds.
     *
     * @return the local conditions time in milliseconds
     */
    public long getLocalConditionsTime() {
        return localConditionsTime;
    }

    /**
     * Sets the local accumulated conditions evaluation time since the last cluster sync.
     *
     * @param localConditionsTime the local conditions time in milliseconds
     */
    public void setLocalConditionsTime(long localConditionsTime) {
        this.localConditionsTime = localConditionsTime;
    }

    /**
     * Cluster-wide accumulated time spent executing rule actions, in milliseconds.
     *
     * @return the cluster actions time in milliseconds
     */
    public long getActionsTime() {
        return actionsTime;
    }

    /**
     * Sets the cluster-wide accumulated actions execution time.
     *
     * @param actionsTime the cluster actions time in milliseconds
     */
    public void setActionsTime(long actionsTime) {
        this.actionsTime = actionsTime;
    }

    /**
     * Local accumulated time spent executing rule actions since the last cluster sync, in milliseconds.
     *
     * @return the local actions time in milliseconds
     */
    public long getLocalActionsTime() {
        return localActionsTime;
    }

    /**
     * Sets the local accumulated actions execution time since the last cluster sync.
     *
     * @param localActionsTime the local actions time in milliseconds
     */
    public void setLocalActionsTime(long localActionsTime) {
        this.localActionsTime = localActionsTime;
    }

    /**
     * Date when local counters were last merged into the cluster totals.
     *
     * @return the last synchronization date
     */
    public Date getLastSyncDate() {
        return lastSyncDate;
    }

    /**
     * Sets the last cluster synchronization date.
     *
     * @param lastSyncDate the last synchronization date
     */
    public void setLastSyncDate(Date lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }
}
