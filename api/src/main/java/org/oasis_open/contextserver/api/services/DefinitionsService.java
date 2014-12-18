package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.PropertyMergeStrategyType;
import org.oasis_open.contextserver.api.Tag;
import org.oasis_open.contextserver.api.ValueType;
import org.oasis_open.contextserver.api.actions.ActionType;
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

    Map<Long, List<PluginType>> getTypesByPlugin();

    PropertyMergeStrategyType getPropertyMergeStrategyType(String id);

}
