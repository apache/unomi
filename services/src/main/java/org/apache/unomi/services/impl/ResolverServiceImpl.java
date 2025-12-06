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

package org.apache.unomi.services.impl;

import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.InvalidObjectInfo;
import org.apache.unomi.api.services.ResolverService;
import org.apache.unomi.api.utils.ParserHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ResolverService that resolves condition types, action types, and value types,
 * with automatic tracking of invalid objects that have unresolved types.
 */
public class ResolverServiceImpl implements ResolverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverServiceImpl.class.getName());
    
    private DefinitionsService definitionsService;
    
    // Map of object type -> Map of object ID -> InvalidObjectInfo
    private final Map<String, Map<String, InvalidObjectInfo>> invalidObjects = new ConcurrentHashMap<>();

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Override
    public boolean resolveConditionType(Condition rootCondition, String contextObjectName) {
        if (definitionsService == null) {
            LOGGER.warn("DefinitionsService not available, cannot resolve condition type for {}", contextObjectName);
            return false;
        }
        return ParserHelper.resolveConditionType(definitionsService, rootCondition, contextObjectName);
    }

    @Override
    public boolean resolveActionTypes(Rule rule, boolean ignoreErrors) {
        if (definitionsService == null) {
            return false;
        }
        return ParserHelper.resolveActionTypes(definitionsService, rule, ignoreErrors);
    }

    @Override
    public boolean resolveActionType(Action action) {
        if (definitionsService == null) {
            return false;
        }
        return ParserHelper.resolveActionType(definitionsService, action);
    }

    @Override
    public void resolveValueType(PropertyType propertyType) {
        if (definitionsService == null || propertyType == null) {
            return;
        }
        ParserHelper.resolveValueType(definitionsService, propertyType);
    }

    @Override
    public boolean resolveCondition(String objectType, MetadataItem item, Condition condition, String contextName) {
        if (condition == null) {
            // Null condition is valid, clear missingPlugins if set
            if (item != null && item.getMetadata() != null && item.getMetadata().isMissingPlugins()) {
                item.getMetadata().setMissingPlugins(false);
            }
            return true;
        }
        
        boolean resolved = resolveConditionType(condition, contextName);
        String objectId = item != null ? item.getItemId() : null;
        
        if (!resolved) {
            // Extract the specific unresolved condition type ID for better error message
            String unresolvedTypeId = condition.getConditionTypeId();
            List<String> missingConditionTypeIds = unresolvedTypeId != null 
                    ? Collections.singletonList(unresolvedTypeId) : Collections.emptyList();
            String reason = unresolvedTypeId != null 
                    ? "Unresolved condition type: " + unresolvedTypeId
                    : "Unresolved condition type";
            if (objectId != null) {
                markInvalid(objectType, objectId, reason, missingConditionTypeIds, null, contextName);
            }
            // Set missingPlugins flag when types can't be resolved
            if (item != null && item.getMetadata() != null) {
                item.getMetadata().setMissingPlugins(true);
            }
        } else {
            if (objectId != null) {
                markValid(objectType, objectId);
            }
            // Clear missingPlugins flag when types are successfully resolved
            if (item != null && item.getMetadata() != null) {
                item.getMetadata().setMissingPlugins(false);
            }
        }
        return resolved;
    }

    @Override
    public boolean resolveActions(String objectType, Rule rule) {
        if (rule == null) {
            return true;
        }
        
        boolean resolved = resolveActionTypes(rule, false);
        String objectId = rule.getItemId();
        
        if (!resolved) {
            // Collect all unresolved action type IDs
            List<String> unresolvedActionIds = new ArrayList<>();
            if (rule.getActions() != null) {
                for (Action action : rule.getActions()) {
                    if (action.getActionType() == null && action.getActionTypeId() != null) {
                        unresolvedActionIds.add(action.getActionTypeId());
                    }
                }
            }
            String reason = unresolvedActionIds.isEmpty()
                    ? "Unresolved action type"
                    : "Unresolved action type(s): " + String.join(", ", unresolvedActionIds);
            if (objectId != null) {
                markInvalid(objectType, objectId, reason, null, unresolvedActionIds, "rule " + objectId);
            }
            // Set missingPlugins flag when types can't be resolved
            if (rule.getMetadata() != null) {
                rule.getMetadata().setMissingPlugins(true);
            }
        } else {
            if (objectId != null) {
                markValid(objectType, objectId);
            }
            // Note: missingPlugins is only cleared when both condition and actions are resolved
            // This is handled in resolveRule() method
        }
        return resolved;
    }

    @Override
    public boolean resolveRule(String objectType, Rule rule) {
        if (rule == null) {
            return true;
        }
        
        String objectId = rule.getItemId();
        String contextName = "rule " + objectId;
        List<String> reasons = new ArrayList<>();
        List<String> missingConditionTypeIds = new ArrayList<>();
        List<String> missingActionTypeIds = new ArrayList<>();
        
        // Resolve condition (without setting missingPlugins yet)
        boolean conditionResolved = true;
        if (rule.getCondition() != null) {
            conditionResolved = resolveConditionType(rule.getCondition(), contextName);
            if (!conditionResolved) {
                String unresolvedTypeId = rule.getCondition().getConditionTypeId();
                if (unresolvedTypeId != null) {
                    missingConditionTypeIds.add(unresolvedTypeId);
                }
                reasons.add("Unresolved condition type" + (unresolvedTypeId != null ? ": " + unresolvedTypeId : ""));
            }
        }
        
        // Resolve actions
        boolean actionsResolved = resolveActionTypes(rule, false);
        if (!actionsResolved) {
            // Collect all unresolved action type IDs
            if (rule.getActions() != null) {
                for (Action action : rule.getActions()) {
                    if (action.getActionType() == null && action.getActionTypeId() != null) {
                        missingActionTypeIds.add(action.getActionTypeId());
                    }
                }
            }
            reasons.add("Unresolved action type(s): " + (missingActionTypeIds.isEmpty() ? "unknown" : String.join(", ", missingActionTypeIds)));
        }
        
        // Set/clear missingPlugins based on both condition and actions resolution
        boolean allResolved = conditionResolved && actionsResolved;
        if (rule.getMetadata() != null) {
            rule.getMetadata().setMissingPlugins(!allResolved);
        }
        
        // Track invalid objects
        if (!allResolved && objectId != null) {
            markInvalid(objectType, objectId, String.join("; ", reasons), 
                       missingConditionTypeIds.isEmpty() ? null : missingConditionTypeIds,
                       missingActionTypeIds.isEmpty() ? null : missingActionTypeIds,
                       contextName);
        } else if (allResolved && objectId != null) {
            markValid(objectType, objectId);
        }
        
        return allResolved;
    }

    // Invalid object tracking methods

    @Override
    public void markInvalid(String objectType, String objectId, String reason) {
        markInvalid(objectType, objectId, reason, null, null, null);
    }

    /**
     * Mark an object as invalid with detailed information.
     * Only logs the first time an object is encountered.
     * 
     * @param objectType the type of object
     * @param objectId the ID of the object
     * @param reason the reason for invalidation
     * @param missingConditionTypeIds list of missing condition type IDs
     * @param missingActionTypeIds list of missing action type IDs
     * @param contextName context where the object was encountered
     */
    private void markInvalid(String objectType, String objectId, String reason,
                            List<String> missingConditionTypeIds,
                            List<String> missingActionTypeIds,
                            String contextName) {
        if (objectType == null || objectId == null) {
            LOGGER.warn("Cannot mark object as invalid: objectType or objectId is null");
            return;
        }
        
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.computeIfAbsent(objectType, k -> new ConcurrentHashMap<>());
        
        InvalidObjectInfo existingInfo = typeMap.get(objectId);
        if (existingInfo != null) {
            // Object already marked invalid - update encounter info but don't log again
            existingInfo.updateEncounter(missingConditionTypeIds, missingActionTypeIds, contextName);
        } else {
            // First time encountering this invalid object - create info and log
            InvalidObjectInfo newInfo = new InvalidObjectInfo(
                objectType, 
                objectId, 
                reason != null ? reason : "Unknown reason",
                missingConditionTypeIds,
                missingActionTypeIds,
                contextName
            );
            typeMap.put(objectId, newInfo);
            
            // Log only on first encounter with detailed information
            StringBuilder logMessage = new StringBuilder("Marked ").append(objectType).append(" ").append(objectId)
                .append(" as invalid: ").append(reason != null ? reason : "Unknown reason");
            
            if (missingConditionTypeIds != null && !missingConditionTypeIds.isEmpty()) {
                logMessage.append(" (missing condition types: ").append(String.join(", ", missingConditionTypeIds)).append(")");
            }
            if (missingActionTypeIds != null && !missingActionTypeIds.isEmpty()) {
                logMessage.append(" (missing action types: ").append(String.join(", ", missingActionTypeIds)).append(")");
            }
            if (contextName != null) {
                logMessage.append(" [context: ").append(contextName).append("]");
            }
            
            LOGGER.warn(logMessage.toString());
        }
    }

    @Override
    public void markValid(String objectType, String objectId) {
        if (objectType == null || objectId == null) {
            return;
        }
        
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.get(objectType);
        if (typeMap != null) {
            InvalidObjectInfo removed = typeMap.remove(objectId);
            if (removed != null) {
                LOGGER.debug("Marked {} {} as valid (removed from invalid tracking)", objectType, objectId);
            }
            // Clean up empty type maps
            if (typeMap.isEmpty()) {
                invalidObjects.remove(objectType);
            }
        }
    }

    @Override
    public boolean isInvalid(String objectType, String objectId) {
        if (objectType == null || objectId == null) {
            return false;
        }
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.get(objectType);
        return typeMap != null && typeMap.containsKey(objectId);
    }

    @Override
    public String getInvalidationReason(String objectType, String objectId) {
        if (objectType == null || objectId == null) {
            return null;
        }
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.get(objectType);
        if (typeMap != null) {
            InvalidObjectInfo info = typeMap.get(objectId);
            return info != null ? info.getReason() : null;
        }
        return null;
    }

    @Override
    public Map<String, Map<String, InvalidObjectInfo>> getAllInvalidObjects() {
        Map<String, Map<String, InvalidObjectInfo>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, InvalidObjectInfo>> entry : invalidObjects.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    @Override
    public Map<String, InvalidObjectInfo> getInvalidObjects(String objectType) {
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.get(objectType);
        return typeMap != null ? new HashMap<>(typeMap) : Collections.emptyMap();
    }

    @Override
    public Map<String, Set<String>> getAllInvalidObjectIds() {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, InvalidObjectInfo>> entry : invalidObjects.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue().keySet()));
        }
        return result;
    }

    @Override
    public Set<String> getInvalidObjectIds(String objectType) {
        Map<String, InvalidObjectInfo> typeMap = invalidObjects.get(objectType);
        return typeMap != null ? new HashSet<>(typeMap.keySet()) : Collections.emptySet();
    }

    @Override
    public int getTotalInvalidObjectCount() {
        return invalidObjects.values().stream()
            .mapToInt(Map::size)
            .sum();
    }
}

