package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Tag;
import org.oasis_open.wemi.context.server.api.actions.ActionType;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;

import java.util.Collection;
import java.util.Set;

public interface DefinitionsService {
    Set<Tag> getAllTags();

    Set<Tag> getRootTags();

    Tag getTag(Tag tag);

    Collection<ConditionType> getAllConditionTypes();

    Set<ConditionType> getConditionTypesByTag(Tag tag);

    ConditionType getConditionType(String name);

    Collection<ActionType> getAllActionTypes();

    Set<ActionType> getActionTypeByTag(Tag tag);

    ActionType getActionType(String name);
}
