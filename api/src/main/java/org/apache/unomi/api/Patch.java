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

public class Patch extends Item {
    private static final long serialVersionUID = 4171966405850833985L;

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

    public void setLastApplication(Date lastApplication) {
        this.lastApplication = lastApplication;
    }
}
