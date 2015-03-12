package org.oasis_open.contextserver.api.services;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;

import java.util.*;

public interface DefinitionsService {
    Set<Tag> getAllTags();

    Set<Tag> getRootTags();

    Tag getTag(String tagId);

    Collection<ConditionType> getAllConditionTypes();

    Set<ConditionType> getConditionTypesByTag(Tag tag, boolean recursive);

    ConditionType getConditionType(String id);

    Collection<ActionType> getAllActionTypes();

    Set<ActionType> getActionTypeByTag(Tag tag, boolean recursive);

    ActionType getActionType(String id);

    Collection<ValueType> getAllValueTypes();

    Set<ValueType> getValueTypeByTag(Tag tag, boolean recursive);

    ValueType getValueType(String id);

    Collection<PropertyType> getAllPropertyTypes(String target);

    HashMap<String, Collection<PropertyType>> getAllPropertyTypes();

    Set<PropertyType> getPropertyTypeByTag(Tag tag, boolean recursive);

    Set<PropertyType> getPropertyTypeByMapping(String propertyName);

    PropertyType getPropertyType(String target, String id);

    Map<Long, List<PluginType>> getTypesByPlugin();

    PropertyMergeStrategyType getPropertyMergeStrategyType(String id);

    Set<Condition> extractConditionsByType(Condition rootCondition, String typeId);

    Condition extractConditionByTag(Condition rootCondition, String tagId);

}
