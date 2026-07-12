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

package org.apache.unomi.api;

import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a patch operation applied to an existing {@link Item}.
 * {@link #PATCHABLE_TYPES} lists the item types that support patching.
 * The {@link #operation} is one of {@code override}, {@code patch}, or
 * {@code remove}: {@code override} replaces the full item, {@code patch}
 * applies a JSON Patch payload, and {@code remove} needs no data.
 */
public class Patch extends Item {
    private static final long serialVersionUID = 4171966405850833985L;

    /**
     * A map containing the types of items that are capable of being patched.
     * The keys represent item type names, and the values are {@link Class}
     * objects corresponding to those patchable item types.
     */
    public final static Map<String, Class<? extends Item>> PATCHABLE_TYPES;

    static {
        PATCHABLE_TYPES = new TreeMap<>();
        PATCHABLE_TYPES.put("condition", ConditionType.class);
        PATCHABLE_TYPES.put("action", ActionType.class);
        PATCHABLE_TYPES.put("goal", Goal.class);
        PATCHABLE_TYPES.put("campaign", Campaign.class);
        PATCHABLE_TYPES.put("persona",Persona.class);
        PATCHABLE_TYPES.put("propertyType",PropertyType.class);
        PATCHABLE_TYPES.put("rule", Rule.class);
        PATCHABLE_TYPES.put("segment", Segment.class);
        PATCHABLE_TYPES.put("scoring", Scoring.class);
    }

    /**
     * The constant string used to identify this class as a "patch" item type.
     */
    public static final String ITEM_TYPE = "patch";

    private String patchedItemId;

    private String patchedItemType;

    private String operation;

    private Object data;

    private Date lastApplication;

    /**
     * Get the id of the item that will be concerned by this patch
     * @return item id
     */
    public String getPatchedItemId() {
        return patchedItemId;
    }

    /**
     * Sets the ID of the item that will be concerned by this patch.
     * @param patchedItemId the id of the item
     */
    public void setPatchedItemId(String patchedItemId) {
        this.patchedItemId = patchedItemId;
    }

    /**
     * Get the item type of the item that will be concerned by this patch
     * @return item type
     */
    public String getPatchedItemType() {
        return patchedItemType;
    }

    /**
     * Sets the item type of the item that will be concerned by this patch.
     * @param patchedItemType the item type
     */
    public void setPatchedItemType(String patchedItemType) {
        this.patchedItemType = patchedItemType;
    }

    /**
     * Get the type of patch operation : override, patch or remove
     * @return operation
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Sets the type of patch operation to perform. This can typically be
     * 'override', 'patch', or 'remove'.
     * @param operation the patch operation type
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * Get the patch data
     * For override operation, the data is the full item
     * For patch, the data is a JsonPatch object
     * For remove, no data is needed
     * @return data
     */
    public Object getData() {
        return data;
    }

    /**
     * Sets the data payload for the patch.
     * For override operations, this should contain the full
     * item representation.
     * For patch operations, this should contain a JsonPatch object.
     * For remove operations, no data is required.
     * @param data the patch data
     */
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * Get the date of the last patch application
     * @return last application date
     */
    public Date getLastApplication() {
        return lastApplication;
    }

    /**
     * Sets the date when the patch was last applied.
     * @param lastApplication the date of the last application
     */
    public void setLastApplication(Date lastApplication) {
        this.lastApplication = lastApplication;
    }
}
