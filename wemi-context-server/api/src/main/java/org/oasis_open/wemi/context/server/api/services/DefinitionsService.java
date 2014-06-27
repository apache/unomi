package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.conditions.ConditionTag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;

import java.util.Set;

public interface DefinitionsService {
    ConditionType getConditionType(String name);
    ConsequenceType getConsequenceType(String name);
    Set<ConditionTag> getConditionTags ();
    Set<ConditionType> getConditions(ConditionTag conditionTag);
}
