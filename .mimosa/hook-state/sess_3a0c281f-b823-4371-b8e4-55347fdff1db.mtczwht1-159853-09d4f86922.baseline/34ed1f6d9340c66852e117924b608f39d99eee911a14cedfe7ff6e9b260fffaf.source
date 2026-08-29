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

import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;

import java.util.Map;
import java.util.Set;

/**
 * Service for resolving condition types, action types, and value types, with automatic
 * tracking of invalid objects that have unresolved types.
 * 
 * This service centralizes type resolution logic and automatically tracks objects
 * that fail to resolve, providing detailed error information.
 */
public interface TypeResolutionService {
    
    /**
     * Resolve a condition type and all its nested conditions.
     * This is a low-level method that only performs resolution - it does NOT track invalid objects
     * or handle missingPlugins. Use {@link #resolveCondition(String, MetadataItem, Condition, String)}
     * for validation contexts where tracking is needed.
     * 
     * @param rootCondition the condition to resolve
     * @param contextObjectName name/ID of the object containing this condition (for error messages)
     * @return true if resolution succeeded, false otherwise
     */
    boolean resolveConditionType(Condition rootCondition, String contextObjectName);
    
    /**
     * Resolve all action types in a rule.
     * This is a low-level method that only performs resolution - it does NOT track invalid objects
     * or handle missingPlugins.
     * 
     * @param rule the rule containing actions to resolve
     * @param ignoreErrors if true, don't log warnings for missing actions
     * @return true if all actions resolved successfully, false otherwise
     */
    boolean resolveActionTypes(Rule rule, boolean ignoreErrors);
    
    /**
     * Resolve a single action type.
     * This is a low-level method that only performs resolution - it does NOT track invalid objects
     * or handle missingPlugins.
     * 
     * @param action the action to resolve
     * @return true if resolution succeeded, false otherwise
     */
    boolean resolveActionType(Action action);
    
    /**
     * Resolve a value type for a property type.
     * 
     * @param propertyType the property type to resolve
     */
    void resolveValueType(PropertyType propertyType);
    
    /**
     * Resolve condition types for a MetadataItem with automatic tracking and missingPlugins handling.
     * 
     * <p>This method resolves condition types (needed for validation) but skips parameter value resolution.
     * Parameter resolution happens on-demand in query builders and evaluators.
     * 
     * <p>This method performs three operations:
     * <ul>
     *   <li>Resolves the condition type (always)</li>
     *   <li>Tracks invalid objects in the tracking service</li>
     *   <li>Sets/clears the missingPlugins flag on the item's metadata</li>
     * </ul>
     * 
     * <p>This is the recommended method for save operations (e.g., when saving rules, segments, goals).
     * 
     * @param objectType the type of object (e.g., "rules", "segments", "goals", "campaigns", "scoring")
     * @param item the MetadataItem object (e.g., Rule, Segment, Goal, Campaign, Scoring)
     * @param condition the condition to resolve (may be null)
     * @param contextName context name for error messages
     * @return true if condition type resolved successfully (or was null), false otherwise
     */
    boolean resolveCondition(String objectType, MetadataItem item, Condition condition, String contextName);
    
    /**
     * Resolve action types for a rule with automatic tracking and missingPlugins handling.
     * 
     * <p>This method resolves action types (needed for validation) but skips parameter value resolution.
     * Parameter resolution happens on-demand in query builders and evaluators.
     * 
     * <p>This method performs three operations:
     * <ul>
     *   <li>Resolves the action types</li>
     *   <li>Tracks invalid objects in the tracking service</li>
     *   <li>Sets/clears the missingPlugins flag on the rule's metadata</li>
     * </ul>
     * 
     * <p>Note: For complete rule validation (condition + actions), use {@link #resolveRule(String, Rule)} instead.
     * 
     * @param objectType the type of object (e.g., "rules")
     * @param rule the rule containing actions to resolve
     * @return true if all action types resolved successfully, false otherwise
     */
    boolean resolveActions(String objectType, Rule rule);
    
    /**
     * Resolve both condition and actions for a rule with automatic tracking and missingPlugins handling.
     * 
     * <p>This method resolves condition and action types (needed for validation) but skips parameter value resolution.
     * Parameter resolution happens on-demand in query builders and evaluators.
     * 
     * <p>This is a convenience method that handles both condition and action resolution together,
     * ensuring missingPlugins is set correctly based on both resolutions.
     * 
     * @param objectType the type of object (e.g., "rules")
     * @param rule the rule to resolve
     * @return true if both condition and action types resolved successfully, false otherwise
     */
    boolean resolveRule(String objectType, Rule rule);
    
    // Invalid object tracking methods
    
    /**
     * Mark an object as invalid with a reason.
     * 
     * @param objectType the type of object (e.g., "rules", "segments", "goals", "campaigns", "scoring")
     * @param objectId the ID of the object
     * @param reason the reason why the object is invalid (e.g., "Unresolved condition type: xyz", "Unresolved action type: abc")
     */
    void markInvalid(String objectType, String objectId, String reason);
    
    /**
     * Mark an object as valid (remove it from invalid tracking).
     * 
     * @param objectType the type of object
     * @param objectId the ID of the object
     */
    void markValid(String objectType, String objectId);
    
    /**
     * Check if an object is invalid.
     * 
     * @param objectType the type of object
     * @param objectId the ID of the object
     * @return true if the object is invalid, false otherwise
     */
    boolean isInvalid(String objectType, String objectId);
    
    /**
     * Get the invalidation reason for an object.
     * 
     * @param objectType the type of object
     * @param objectId the ID of the object
     * @return the reason why the object is invalid, or null if the object is valid
     */
    String getInvalidationReason(String objectType, String objectId);
    
    /**
     * Get all invalid objects grouped by object type, with their reasons.
     * 
     * @return a map where keys are object type names (e.g., "rules", "segments", "goals", "campaigns", "scoring")
     *         and values are maps of object ID to InvalidObjectInfo
     */
    Map<String, Map<String, InvalidObjectInfo>> getAllInvalidObjects();
    
    /**
     * Get invalid objects for a specific object type, with their reasons.
     * 
     * @param objectType the object type (e.g., "rules", "segments", "goals", "campaigns", "scoring")
     * @return map of object ID to InvalidObjectInfo for the specified type, or empty map if type is unknown
     */
    Map<String, InvalidObjectInfo> getInvalidObjects(String objectType);
    
    /**
     * Get all invalid object IDs grouped by object type (for backward compatibility).
     * 
     * @return a map where keys are object type names and values are sets of invalid object IDs
     */
    Map<String, Set<String>> getAllInvalidObjectIds();
    
    /**
     * Get invalid object IDs for a specific object type (for backward compatibility).
     * 
     * @param objectType the object type
     * @return set of invalid object IDs for the specified type, or empty set if type is unknown
     */
    Set<String> getInvalidObjectIds(String objectType);
    
    /**
     * Get the total count of all invalid objects across all types.
     * 
     * @return total count of invalid objects
     */
    int getTotalInvalidObjectCount();
}

