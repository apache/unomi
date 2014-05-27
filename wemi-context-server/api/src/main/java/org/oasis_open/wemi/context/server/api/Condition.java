package org.oasis_open.wemi.context.server.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a condition. Conditions are then composed using ConditionNodes to build segment definitions.
 */
public class Condition {

    Set<ConditionTag> conditionTags = new TreeSet<ConditionTag>();

    List<ConditionParameter> conditionParameters = new ArrayList<ConditionParameter>();

}
