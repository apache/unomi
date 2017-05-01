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
 * A separate item to track rule statistics, because we will manage the persistence and updating of
 * these seperately from the rules themselves. This object contains all the relevant statistics
 * concerning the execution of a rule, including accumulated execution times.
 */
public class RuleStatistics extends Item {

    /**
     * The RuleStatistics ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "rulestats";
    private static final long serialVersionUID = 1L;

    private long executionCount = 0;
    private long localExecutionCount = 0;
    private long conditionsTime = 0;
    private long localConditionsTime = 0;
    private long actionsTime = 0;
    private long localActionsTime = 0;
    private Date lastSyncDate;

    public RuleStatistics() {
    }

    public RuleStatistics(String itemId) {
        super(itemId);
    }

    /**
     * Retrieve the execution count of the rule in the cluster
     * @return a long that is the total number of executions of the rule without the local node execution
     * count
     */
    public long getExecutionCount() {
        return executionCount;
    }

    /**
     * Set the execution count of the rule in the cluster
     * @param executionCount a long that represents the number of execution of the rule in the cluster
     */
    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    /**
     * Retrieve the execution count of the rule on this single node since the last sync with the cluster
     * @return a long that is the total number of executions on this node since the last sync with the
     * cluster
     */
    public long getLocalExecutionCount() {
        return localExecutionCount;
    }

    /**
     * Sets the number of local execution counts for this node since the last sync with the cluster
     * @param localExecutionCount a long that represents the number of execution of the rule since the
     *                            last sync with the cluster
     */
    public void setLocalExecutionCount(long localExecutionCount) {
        this.localExecutionCount = localExecutionCount;
    }

    /**
     * Retrieve the accumulated time evaluating the conditions of the rule in the cluster
     * @return a long representing the accumulated time in milliseconds that represents the time spent
     * evaluating the conditions of the rule for the whole cluster
     */
    public long getConditionsTime() {
        return conditionsTime;
    }

    /**
     * Sets the execution time of the condition of the rule for the whole cluster
     * @param conditionsTime a long representing a time in milliseconds
     */
    public void setConditionsTime(long conditionsTime) {
        this.conditionsTime = conditionsTime;
    }

    /**
     * Retrieve the accumulated execution time of the rule's condition since the last sync with the cluster
     * @return a long that represents the accumulated time in milliseconds
     */
    public long getLocalConditionsTime() {
        return localConditionsTime;
    }

    /**
     * Sets the accumulated execution time of the rule's condition since the last sync with the cluster
     * @param localConditionsTime a long that represents the accumulated time in milliseconds
     */
    public void setLocalConditionsTime(long localConditionsTime) {
        this.localConditionsTime = localConditionsTime;
    }

    /**
     * Retrieve the accumulated time of the rule's actions
     * @return a long representing the accumulated time in milliseconds
     */
    public long getActionsTime() {
        return actionsTime;
    }

    /**
     * Sets the accumulated time for the rule's actions
     * @param actionsTime a long representing the accumulated time in milliseconds
     */
    public void setActionsTime(long actionsTime) {
        this.actionsTime = actionsTime;
    }

    /**
     * Retrieve the accumulated time spent executing the rule's actions since the last sync with the cluster
     * @return a long representing the accumulated time in milliseconds
     */
    public long getLocalActionsTime() {
        return localActionsTime;
    }

    /**
     * Sets the accumulated time spend executing the rule's actions since the last sync with the cluster
     * @param localActionsTime a long representing the accumulated time in milliseconds
     */
    public void setLocalActionsTime(long localActionsTime) {
        this.localActionsTime = localActionsTime;
    }

    /**
     * Retrieve the last sync date
     * @return a date that was set the last time the statistics were synchronized with the cluster
     */
    public Date getLastSyncDate() {
        return lastSyncDate;
    }

    /**
     * Sets the last sync date
     * @param lastSyncDate a date that represents the last time the statistics were synchronized
     *                     with the cluster
     */
    public void setLastSyncDate(Date lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }
}
