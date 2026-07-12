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
 * Diagnostic record for a configuration item that failed validation.
 * Lists missing condition or action types and the contexts where the
 * invalid object was seen so operators can fix definitions.
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
     * Constructs an {@code InvalidObjectInfo} object using only the basic
     * identifying information.
     * This constructor delegates to the full constructor, initializing missing
     * lists and context names as null or empty.
     * @param objectType The type name of the invalid object. Must not be null.
     * @param objectId The unique ID of the invalid object. Must not be null.
     * @param reason A detailed explanation for why the object
     * is considered invalid.
     */
    public InvalidObjectInfo(String objectType, String objectId, String reason) {
        this(objectType, objectId, reason, null, null, null);
    }

    /**
     * Constructs an {@code InvalidObjectInfo} object with full details,
     * including initial tracking information.
     * This initializes timestamps and encounter counts upon creation.
     * @param objectType The type name of the invalid object. Must not be null.
     * @param objectId The unique ID of the invalid object. Must not be null.
     * @param reason A detailed explanation for why the object
     * is considered invalid.
     * @param missingConditionTypeIds Initial list of condition types that were
     * missing during validation.
     * @param missingActionTypeIds Initial list of action types that were
     * missing during validation.
     * @param contextName The specific context where this initial encounter
     * occurred. Can be null.
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
     * Retrieves the type name associated with this invalid object record.
     * @return The {@code String} representing the object's type name.
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Retrieves the unique ID of the invalid object record.
     * @return The {@code String} representing the object's ID.
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Retrieves the reason why this object was flagged as invalid.
     * @return The detailed explanation for the invalidation, or {@code null}
     * if none is provided.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the timestamp when this object record was first observed as invalid.
     * @return The initial {@code long} timestamp (milliseconds since epoch).
     */
    public long getFirstSeenTimestamp() {
        return firstSeenTimestamp;
    }

    /**
     * Gets the most recent timestamp when this object record was
     * observed as invalid.
     * This value is updated whenever an encounter occurs.
     * @return The latest {@code long} timestamp (milliseconds since epoch).
     */
    public long getLastSeenTimestamp() {
        return lastSeenTimestamp.get();
    }

    /**
     * Returns the total number of times this object has been
     * encountered as invalid.
     * @return An integer count representing the cumulative encounters.
     */
    public int getEncounterCount() {
        return encounterCount.get();
    }

    /**
     * Retrieves an unmodifiable list of condition type IDs that were found to
     * be missing or unresolved for this object instance.
     * @return A {@link java.util.List} containing the
     * missing condition type IDs.
     */
    public List<String> getMissingConditionTypeIds() {
        return Collections.unmodifiableList(new ArrayList<>(missingConditionTypeIds));
    }

    /**
     * Retrieves an unmodifiable list of action type IDs that were found to be
     * missing or unresolved for this object instance.
     * @return A {@link java.util.List} containing the missing action type IDs.
     */
    public List<String> getMissingActionTypeIds() {
        return Collections.unmodifiableList(new ArrayList<>(missingActionTypeIds));
    }

    /**
     * Returns an unmodifiable set of context names in which this invalid
     * object was encountered.
     * @return A {@link java.util.Set} containing all recorded context names.
     */
    public Set<String> getContextNames() {
        return Collections.unmodifiableSet(contextNames);
    }

    /**
     * Updates tracking info when the object is encountered again during type resolution.
     * Thread-safe: backed by CopyOnWriteArrayList/CopyOnWriteArraySet, so reads via
     * {@code getMissingConditionTypeIds()}, {@code getMissingActionTypeIds()}, and
     * {@code getContextNames()} are safe during concurrent writes.
     * @param missingConditionTypeIds additional missing condition type IDs found in this encounter
     * @param missingActionTypeIds    additional missing action type IDs found in this encounter
     * @param contextName             context where this encounter occurred
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

