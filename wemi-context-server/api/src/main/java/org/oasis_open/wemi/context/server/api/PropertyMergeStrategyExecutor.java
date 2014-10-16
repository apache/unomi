package org.oasis_open.wemi.context.server.api;

import java.util.List;

/**
 * Classes implementing this interface will implement an algorithm to merge user properties based on different strategies
 * such as "adding integers", "using oldest value", "using most recent value", "merging lists", etc...
 */
public interface PropertyMergeStrategyExecutor {

    public User mergeProperty(String propertyName, PropertyType propertyType, List<User> usersToMerge, User targetUser);

}
