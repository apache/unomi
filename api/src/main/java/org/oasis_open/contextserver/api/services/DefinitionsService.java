package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    Set<PropertyType> getPropertyTypeByTag(Tag tag, boolean recursive);

    Set<PropertyType> getPropertyTypeByMapping(String propertyName);

    PropertyType getPropertyType(String target, String id);

    Map<Long, List<PluginType>> getTypesByPlugin();

    PropertyMergeStrategyType getPropertyMergeStrategyType(String id);

    Set<Condition> extractConditionsByType(Condition rootCondition, String typeId);

    Condition extractConditionByTag(Condition rootCondition, String tagId);

}
