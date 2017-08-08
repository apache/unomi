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

import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A service to retrieve definition information about core context server entities such as conditions, actions and values.
 */
public interface DefinitionsService {
    /**
     * Retrieves all condition types.
     *
     * @return a Collection of all collection types
     */
    Collection<ConditionType> getAllConditionTypes();

    /**
     * Retrieves the set of condition types with the specified tag also retrieving condition types from sub-tags if so specified.
     *
     * @param tag                the tag marking the condition types we want to retrieve
     * @return the set of condition types with the specified tag (and its sub-tags, if specified)
     */
    Set<ConditionType> getConditionTypesByTag(String tag);

    /**
     * Retrieves the condition type associated with the specified identifier.
     *
     * @param id the identifier of the condition type to retrieve
     * @return the condition type associated with the specified identifier or {@code null} if no such condition type exists
     */
    ConditionType getConditionType(String id);

    /**
     * Stores the condition type
     *
     * @param conditionType the condition type to store
     */
    void setConditionType(ConditionType conditionType);

    /**
     * Remove the condition type
     *
     * @param id the condition type to remove
     */
    void removeConditionType(String id);

    /**
     * Retrieves all known action types.
     *
     * @return all known action types
     */
    Collection<ActionType> getAllActionTypes();

    /**
     * Retrieves the set of action types with the specified tag also retrieving action types from sub-tags if so specified.
     *
     * @param tag                the tag marking the action types we want to retrieve
     * @return the set of action types with the specified tag (and its sub-tags, if specified)
     */
    Set<ActionType> getActionTypeByTag(String tag);

    /**
     * Retrieves the action type associated with the specified identifier.
     *
     * @param id the identifier of the action type to retrieve
     * @return the action type associated with the specified identifier or {@code null} if no such action type exists
     */
    ActionType getActionType(String id);

    /**
     * Stores the action type
     *
     * @param actionType the action type to store
     */
    void setActionType(ActionType actionType);

    /**
     * Remove the action type
     *
     * @param id the action type to remove
     */
    void removeActionType(String id);

    /**
     * Retrieves all known value types.
     *
     * @return all known value types
     */
    Collection<ValueType> getAllValueTypes();

    /**
     * Retrieves the set of value types with the specified tag also retrieving value types from sub-tags if so specified.
     *
     * @param tag                the tag marking the value types we want to retrieve
     * @return the set of value types with the specified tag (and its sub-tags, if specified)
     */
    Set<ValueType> getValueTypeByTag(String tag);

    /**
     * Retrieves the value type associated with the specified identifier.
     *
     * @param id the identifier of the value type to retrieve
     * @return the value type associated with the specified identifier or {@code null} if no such value type exists
     */
    ValueType getValueType(String id);

    /**
     * Retrieves a Map of plugin identifier to a list of plugin types defined by that particular plugin.
     *
     * @return a Map of plugin identifier to a list of plugin types defined by that particular plugin
     */
    Map<Long, List<PluginType>> getTypesByPlugin();

    /**
     * Retrieves the property merge strategy type associated with the specified identifier.
     *
     * @param id the identifier of the property merge strategy type to retrieve
     * @return the property merge strategy type associated with the specified identifier or {@code null} if no such property merge strategy type exists
     */
    PropertyMergeStrategyType getPropertyMergeStrategyType(String id);

    /**
     * Retrieves all conditions of the specified type from the specified root condition.
     *
     * TODO: remove?
     *
     * @param rootCondition the condition from which we want to extract all conditions with the specified type
     * @param typeId the identifier of the condition type we want conditions to extract to match
     * @return a set of conditions contained in the specified root condition and matching the specified condition type or an empty set if no such condition exists
     */
    Set<Condition> extractConditionsByType(Condition rootCondition, String typeId);

    /**
     * Retrieves a condition matching the specified tag identifier from the specified root condition.
     *
     * TODO: remove from API and move to a different class?
     * TODO: purpose and behavior not clear
     *
     * @param rootCondition
     * @param tag
     * @return
     */
    Condition extractConditionByTag(Condition rootCondition, String tag);

    /**
     * Resolves (if possible) the {@link ConditionType}s for the specified condition and its sub-conditions (if any) from the type identifiers existing on the specified condition
     *
     * TODO: remove from API and move to a different class?
     *
     * @param rootCondition the condition for which we want to resolve the condition types from the existing condition type identifiers
     * @return {@code true}
     */
    boolean resolveConditionType(Condition rootCondition);
}