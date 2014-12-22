package org.oasis_open.contextserver.api;

import java.util.List;

/**
 * Classes implementing this interface will implement an algorithm to merge profile properties based on different strategies
 * such as "adding integers", "using oldest value", "using most recent value", "merging lists", etc...
 */
public interface PropertyMergeStrategyExecutor {

    public Profile mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile);

}
