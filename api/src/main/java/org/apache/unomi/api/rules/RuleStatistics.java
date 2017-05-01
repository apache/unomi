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
 * A separate item to track rule statistics, because we will manage the persistence and updating of these seperately
 * from the rules themselves. This object contains all the relevant statistics concerning the execution of a rule.
 */
public class RuleStatistics extends Item {

    /**
     * The Rule ITEM_TYPE.
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

    public long getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(long executionCount) {
        this.executionCount = executionCount;
    }

    public long getLocalExecutionCount() {
        return localExecutionCount;
    }

    public void setLocalExecutionCount(long localExecutionCount) {
        this.localExecutionCount = localExecutionCount;
    }

    public long getConditionsTime() {
        return conditionsTime;
    }

    public void setConditionsTime(long conditionsTime) {
        this.conditionsTime = conditionsTime;
    }

    public long getLocalConditionsTime() {
        return localConditionsTime;
    }

    public void setLocalConditionsTime(long localConditionsTime) {
        this.localConditionsTime = localConditionsTime;
    }

    public long getActionsTime() {
        return actionsTime;
    }

    public void setActionsTime(long actionsTime) {
        this.actionsTime = actionsTime;
    }

    public long getLocalActionsTime() {
        return localActionsTime;
    }

    public void setLocalActionsTime(long localActionsTime) {
        this.localActionsTime = localActionsTime;
    }

    public Date getLastSyncDate() {
        return lastSyncDate;
    }

    public void setLastSyncDate(Date lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }
}
