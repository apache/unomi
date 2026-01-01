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

package org.apache.unomi.api.services;

import java.util.*;

/**
 * Information about an invalid object, including detailed reasons for invalidation.
 */
public class InvalidObjectInfo {
    private final String objectType;
    private final String objectId;
    private final String reason;
    private final long firstSeenTimestamp;
    private volatile long lastSeenTimestamp;
    private volatile int encounterCount;
    private final List<String> missingConditionTypeIds;
    private final List<String> missingActionTypeIds;
    private final Set<String> contextNames;

    public InvalidObjectInfo(String objectType, String objectId, String reason) {
        this(objectType, objectId, reason, null, null, null);
    }

    public InvalidObjectInfo(String objectType, String objectId, String reason, 
                             List<String> missingConditionTypeIds, 
                             List<String> missingActionTypeIds,
                             String contextName) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.reason = reason;
        this.firstSeenTimestamp = System.currentTimeMillis();
        this.lastSeenTimestamp = this.firstSeenTimestamp;
        this.encounterCount = 1;
        this.missingConditionTypeIds = missingConditionTypeIds != null 
            ? new ArrayList<>(missingConditionTypeIds) : new ArrayList<>();
        this.missingActionTypeIds = missingActionTypeIds != null 
            ? new ArrayList<>(missingActionTypeIds) : new ArrayList<>();
        this.contextNames = new HashSet<>();
        if (contextName != null) {
            this.contextNames.add(contextName);
        }
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getReason() {
        return reason;
    }

    public long getFirstSeenTimestamp() {
        return firstSeenTimestamp;
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public int getEncounterCount() {
        return encounterCount;
    }

    public List<String> getMissingConditionTypeIds() {
        return Collections.unmodifiableList(missingConditionTypeIds);
    }

    public List<String> getMissingActionTypeIds() {
        return Collections.unmodifiableList(missingActionTypeIds);
    }

    public Set<String> getContextNames() {
        return Collections.unmodifiableSet(contextNames);
    }

    /**
     * Update the info when the object is encountered again.
     * This method is called internally by TypeResolutionService to track repeated encounters.
     * 
     * @param missingConditionTypeIds additional missing condition type IDs
     * @param missingActionTypeIds additional missing action type IDs
     * @param contextName context where it was encountered
     */
    public void updateEncounter(List<String> missingConditionTypeIds, 
                       List<String> missingActionTypeIds,
                       String contextName) {
        this.lastSeenTimestamp = System.currentTimeMillis();
        this.encounterCount++;
        
        if (missingConditionTypeIds != null) {
            for (String typeId : missingConditionTypeIds) {
                if (!this.missingConditionTypeIds.contains(typeId)) {
                    this.missingConditionTypeIds.add(typeId);
                }
            }
        }
        
        if (missingActionTypeIds != null) {
            for (String typeId : missingActionTypeIds) {
                if (!this.missingActionTypeIds.contains(typeId)) {
                    this.missingActionTypeIds.add(typeId);
                }
            }
        }
        
        if (contextName != null) {
            this.contextNames.add(contextName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidObjectInfo that = (InvalidObjectInfo) o;
        return Objects.equals(objectType, that.objectType) && Objects.equals(objectId, that.objectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectType, objectId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InvalidObjectInfo{");
        sb.append("objectType='").append(objectType).append('\'');
        sb.append(", objectId='").append(objectId).append('\'');
        sb.append(", reason='").append(reason).append('\'');
        sb.append(", firstSeen=").append(firstSeenTimestamp);
        sb.append(", lastSeen=").append(lastSeenTimestamp);
        sb.append(", encounters=").append(encounterCount);
        if (!missingConditionTypeIds.isEmpty()) {
            sb.append(", missingConditionTypes=").append(missingConditionTypeIds);
        }
        if (!missingActionTypeIds.isEmpty()) {
            sb.append(", missingActionTypes=").append(missingActionTypeIds);
        }
        if (!contextNames.isEmpty()) {
            sb.append(", contexts=").append(contextNames);
        }
        sb.append('}');
        return sb.toString();
    }
}

