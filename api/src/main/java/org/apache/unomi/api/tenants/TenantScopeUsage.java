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
 * Per-scope slice of a {@link TenantUsage} snapshot.
 * When usage collection runs, each scope contributes segment and rule counts so
 * operators can see which sites or applications consume the most configuration
 * inside a tenant.
 */
public class TenantScopeUsage {

    private String scopeId;
    private long segmentCount;
    private long ruleCount;

    /**
     * Scope identifier.
     *
     * @return scope id
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * Sets the scope identifier.
     *
     * @param scopeId scope id
     */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * Number of segments in this scope.
     *
     * @return segment count
     */
    public long getSegmentCount() {
        return segmentCount;
    }

    /**
     * Sets the segment count.
     *
     * @param segmentCount segment count
     */
    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * Number of rules in this scope.
     *
     * @return rule count
     */
    public long getRuleCount() {
        return ruleCount;
    }

    /**
     * Sets the rule count.
     *
     * @param ruleCount rule count
     */
    public void setRuleCount(long ruleCount) {
        this.ruleCount = ruleCount;
    }
}
