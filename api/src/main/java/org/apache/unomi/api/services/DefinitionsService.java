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
import org.apache.unomi.api.utils.ConditionBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of built-in and plugin condition, action, and value type definitions.
 * Used when loading rules from JSON, validating conditions, and resolving
 * type metadata in the administration UI.
 */
public interface DefinitionsService {
    /**
     * Returns every registered condition type.
     *
     * @return all condition types
     */
    Collection<ConditionType> getAllConditionTypes();

    /**
     * Returns condition types tagged with the given tag, including sub-tags.
     *
     * @param tag tag marking the condition types to include
     * @return condition types with the specified tag
     */
    Set<ConditionType> getConditionTypesByTag(String tag);

    /**
     * Returns condition types with the given system tag, including sub-tags.
     *
     * @param tag system tag marking the condition types to include
     * @return condition types with the specified system tag
     */
    Set<ConditionType> getConditionTypesBySystemTag(String tag);

    /**
     * Looks up a condition type by id.
     *
     * @param id condition type identifier
     * @return matching condition type, or {@code null} if none exists
     */
    ConditionType getConditionType(String id);

    /**
     * Registers or updates a condition type definition.
     *
     * @param conditionType condition type to store
     */
    void setConditionType(ConditionType conditionType);

    /**
     * Removes a condition type definition.
     *
     * @param id identifier of the condition type to remove
     */
    void removeConditionType(String id);

    /**
     * Returns every registered action type.
     *
     * @return all action types
     */
    Collection<ActionType> getAllActionTypes();

    /**
     * Returns action types tagged with the given tag.
     *
     * @param tag tag marking the action types to include
     * @return action types with the specified tag
     */
    Set<ActionType> getActionTypeByTag(String tag);

    /**
     * Returns action types with the given system tag.
     *
     * @param tag system tag marking the action types to include
     * @return action types with the specified system tag
     */
    Set<ActionType> getActionTypeBySystemTag(String tag);

    /**
     * Looks up an action type by id.
     *
     * @param id action type identifier
     * @return matching action type, or {@code null} if none exists
     */
    ActionType getActionType(String id);

    /**
     * Registers or updates an action type definition.
     *
     * @param actionType action type to store
     */
    void setActionType(ActionType actionType);

    /**
     * Removes an action type definition.
     *
     * @param id identifier of the action type to remove
     */
    void removeActionType(String id);

    /**
     * Returns every registered value type.
     *
     * @return all value types
     */
    Collection<ValueType> getAllValueTypes();

    /**
     * Returns value types tagged with the given tag.
     *
     * @param tag tag marking the value types to include
     * @return value types with the specified tag
     */
    Set<ValueType> getValueTypeByTag(String tag);

    /**
     * Looks up a value type by id.
     *
     * @param id value type identifier
     * @return matching value type, or {@code null} if none exists
     */
    ValueType getValueType(String id);

    /**
     * Registers or updates a value type definition.
     *
     * @param valueType value type to store
     */
    void setValueType(ValueType valueType);

    /**
     * Removes a value type definition.
     *
     * @param id identifier of the value type to remove
     */
    void removeValueType(String id);

    /**
     * Groups registered plugin types by plugin id.
     *
     * @return map of plugin id to plugin types defined by that plugin
     */
    Map<Long, List<PluginType>> getTypesByPlugin();

    /**
     * Looks up a property merge strategy type by id.
     *
     * @param id property merge strategy type identifier
     * @return matching type, or {@code null} if none exists
     */
    PropertyMergeStrategyType getPropertyMergeStrategyType(String id);

    /**
     * Registers or updates a property merge strategy type.
     *
     * @param propertyMergeStrategyType property merge strategy type to store
     */
    void setPropertyMergeStrategyType(PropertyMergeStrategyType propertyMergeStrategyType);

    /**
     * Removes a property merge strategy type definition.
     *
     * @param id identifier of the property merge strategy type to remove
     */
    void removePropertyMergeStrategyType(String id);

    /**
     * Returns every registered property merge strategy type.
     *
     * @return all property merge strategy types
     */
    Collection<PropertyMergeStrategyType> getAllPropertyMergeStrategyTypes();

    /**
     * Collects nested conditions of the given type from a root condition tree.
     *
     *
     * @param rootCondition condition tree to walk
     * @param typeId condition type id to match
     * @return matching nested conditions, or an empty list if none
     */
    List<Condition> extractConditionsByType(Condition rootCondition, String typeId);

    /**
     * Finds the first nested condition tagged with the given tag in a root condition tree.
     *
     * Deprecated helper that may move out of this service in a future release.     *
     * @param rootCondition condition tree to walk
     * @param tag tag used to select a condition
     * @return first matching condition, or {@code null} if none
     * @deprecated As of 1.2.0-incubating, please use {@link #extractConditionBySystemTag(Condition, String)} instead
     */
    @Deprecated
    Condition extractConditionByTag(Condition rootCondition, String tag);

    /**
     * Finds the first nested condition with the given system tag in a root condition tree.
     *
     * @param rootCondition condition tree to walk
     * @param systemTag system tag used to select a condition
     * @return first matching condition, or {@code null} if none
     */
    Condition extractConditionBySystemTag(Condition rootCondition, String systemTag);

    /**
     * @deprecated Use {@link #getTypeResolutionService()} for resolution only, or
     * {@link #getConditionValidationService()} for resolution + validation.
     * This method will be removed in a future version.
     *
     * <p>For resolution only (query operations):
     * <pre>{@code
     * definitionsService.getTypeResolutionService()
     *     .resolveConditionType(condition, "query");
     * }</pre>
     *
     * <p>For resolution + validation (save operations):
     * <pre>{@code
     * List<ValidationError> errors = definitionsService.getConditionValidationService()
     *     .validate(condition);
     * // Handle errors...
     * }</pre>
     *
     * @param rootCondition the condition for which we want to resolve the condition types from the existing condition type identifiers
     * @return {@code true} if resolution succeeded
     */
    @Deprecated
    boolean resolveConditionType(Condition rootCondition);

    /**
     * Refreshes the definitions service, reloading all types from persistence.
     */
    void refresh();

    /**
     * Returns the shared condition builder for programmatic condition construction.
     *
     * @return condition builder instance
     */
    ConditionBuilder getConditionBuilder();

    /**
     * Returns the type resolution service for condition and action type lookup.
     *
     * @return type resolution service instance
     */
    TypeResolutionService getTypeResolutionService();

    /**
     * Returns the condition validation service, which resolves types before validating parameters.
     *
     * @return condition validation service instance
     */
    ConditionValidationService getConditionValidationService();
}
