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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulated record for a definition that references missing condition or action types.
 * {@link org.apache.unomi.api.services.TypeResolutionService} creates and updates
 * these entries when JSON rules, segments, or other items fail type resolution.
 * Operators use the missing-type lists and encounter counts to fix broken imports.
 */
public class InvalidObjectInfo {
    private final String objectType;
    private final String objectId;
    private final String reason;
    private final long firstSeenTimestamp;
    private final AtomicLong lastSeenTimestamp;
    private final AtomicInteger encounterCount;
    private final Set<String> missingConditionTypeIds;  // CopyOnWriteArraySet — atomic add-if-absent, no TOCTOU
    private final Set<String> missingActionTypeIds;
    private final Set<String> contextNames;

    /**
     * Creates a record with type, id, and reason only.
     *
     * @param objectType invalid object type
     * @param objectId invalid object id
     * @param reason why the object is invalid
     */
    public InvalidObjectInfo(String objectType, String objectId, String reason) {
        this(objectType, objectId, reason, null, null, null);
    }

    /**
     * Creates a record with initial missing-type and context details.
     *
     * @param objectType invalid object type
     * @param objectId invalid object id
     * @param reason why the object is invalid
     * @param missingConditionTypeIds missing condition types from the first encounter, or {@code null}
     * @param missingActionTypeIds missing action types from the first encounter, or {@code null}
     * @param contextName context where the object was first seen, or {@code null}
     */
    public InvalidObjectInfo(String objectType, String objectId, String reason,
                             List<String> missingConditionTypeIds,
                             List<String> missingActionTypeIds,
                             String contextName) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.reason = reason;
        this.firstSeenTimestamp = System.currentTimeMillis();
        this.lastSeenTimestamp = new AtomicLong(this.firstSeenTimestamp);
        this.encounterCount = new AtomicInteger(1);
        this.missingConditionTypeIds = missingConditionTypeIds != null
            ? new CopyOnWriteArraySet<>(missingConditionTypeIds) : new CopyOnWriteArraySet<>();
        this.missingActionTypeIds = missingActionTypeIds != null
            ? new CopyOnWriteArraySet<>(missingActionTypeIds) : new CopyOnWriteArraySet<>();
        this.contextNames = new CopyOnWriteArraySet<>();
        if (contextName != null) {
            this.contextNames.add(contextName);
        }
    }

    /**
     * Invalid object type.
     *
     * @return object type
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Invalid object id.
     *
     * @return object id
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Why the object failed validation.
     *
     * @return reason, or {@code null} if unset
     */
    public String getReason() {
        return reason;
    }

    /**
     * When this record was first created (milliseconds since epoch).
     *
     * @return first-seen timestamp
     */
    public long getFirstSeenTimestamp() {
        return firstSeenTimestamp;
    }

    /**
     * When this object was last seen as invalid (milliseconds since epoch).
     *
     * @return last-seen timestamp
     */
    public long getLastSeenTimestamp() {
        return lastSeenTimestamp.get();
    }

    /**
     * How many times this invalid object has been encountered.
     *
     * @return encounter count
     */
    public int getEncounterCount() {
        return encounterCount.get();
    }

    /**
     * Condition types that could not be resolved for this object.
     *
     * @return unmodifiable list of missing condition type ids
     */
    public List<String> getMissingConditionTypeIds() {
        return Collections.unmodifiableList(new ArrayList<>(missingConditionTypeIds));
    }

    /**
     * Action types that could not be resolved for this object.
     *
     * @return unmodifiable list of missing action type ids
     */
    public List<String> getMissingActionTypeIds() {
        return Collections.unmodifiableList(new ArrayList<>(missingActionTypeIds));
    }

    /**
     * Contexts where this invalid object was seen.
     *
     * @return unmodifiable set of context names
     */
    public Set<String> getContextNames() {
        return Collections.unmodifiableSet(contextNames);
    }

    /**
     * Updates tracking info when the object is encountered again during type resolution.
     * Thread-safe: backed by CopyOnWriteArrayList/CopyOnWriteArraySet, so reads via
     * {@code getMissingConditionTypeIds()}, {@code getMissingActionTypeIds()}, and
     * {@code getContextNames()} are safe during concurrent writes.
     *
     * @param missingConditionTypeIds additional missing condition type ids from this encounter
     * @param missingActionTypeIds additional missing action type ids from this encounter
     * @param contextName context where this encounter occurred
     */
    public void updateEncounter(List<String> missingConditionTypeIds,
                                List<String> missingActionTypeIds,
                                String contextName) {
        this.lastSeenTimestamp.set(System.currentTimeMillis());
        this.encounterCount.incrementAndGet();

        if (missingConditionTypeIds != null) {
            this.missingConditionTypeIds.addAll(missingConditionTypeIds);
        }

        if (missingActionTypeIds != null) {
            this.missingActionTypeIds.addAll(missingActionTypeIds);
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
        sb.append(", lastSeen=").append(lastSeenTimestamp.get());
        sb.append(", encounters=").append(encounterCount.get());
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
