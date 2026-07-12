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
package org.apache.unomi.api.tenants;

/**
 * Usage counters for a single scope inside a tenant usage snapshot.
 * Tracks how many segments and rules exist in that scope when
 * {@link TenantUsageService} collects statistics.
 */
public class TenantScopeUsage {

    private String scopeId;
    private long segmentCount;
    private long ruleCount;

    /**
     * Retrieves the unique identifier of the scope.
     * @return The scope ID as a {@link String}.
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * Sets the unique identifier of the scope.
     * @param scopeId The new scope ID to set.
     */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * Retrieves the count of segments associated with this scope usage.
     * @return The segment count as a {@link Long}.
     */
    public long getSegmentCount() {
        return segmentCount;
    }

    /**
     * Sets the count of segments associated with this scope usage.
     * @param segmentCount The new segment count to set.
     */
    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * Retrieves the count of rules associated with this scope usage.
     * @return The rule count as a {@link Long}.
     */
    public long getRuleCount() {
        return ruleCount;
    }

    /**
     * Sets the count of rules associated with this scope usage.
     * @param ruleCount The new rule count to set.
     */
    public void setRuleCount(long ruleCount) {
        this.ruleCount = ruleCount;
    }
}
