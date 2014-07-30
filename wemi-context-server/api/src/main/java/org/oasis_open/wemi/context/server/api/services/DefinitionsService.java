package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;

import java.util.Collection;
import java.util.Set;

public interface DefinitionsService {
    Set<Tag> getAllTags();
    Set<Tag> getRootTags();
    Tag getTag(Tag tag);

    Collection<ConditionType> getAllConditionTypes();
    Set<ConditionType> getConditionTypesByTag(Tag tag);
    ConditionType getConditionType(String name);

    Collection<ConsequenceType> getAllConsequenceTypes();
    Set<ConsequenceType> getConsequenceTypeByTag(Tag tag);
    ConsequenceType getConsequenceType(String name);
}
