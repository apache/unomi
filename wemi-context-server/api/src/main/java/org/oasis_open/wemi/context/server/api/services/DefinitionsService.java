package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;

import java.util.Collection;
import java.util.Set;

public interface DefinitionsService {
    ConditionType getConditionType(String name);
    ConsequenceType getConsequenceType(String name);
    Set<Tag> getAllTags();
    Set<Tag> getRootTags();
    Set<Tag> getChildTags(Tag tag);
    Collection<ConditionType> getAllConditions();
    Set<ConditionType> getConditionTypesByTag(Tag tag);
}
