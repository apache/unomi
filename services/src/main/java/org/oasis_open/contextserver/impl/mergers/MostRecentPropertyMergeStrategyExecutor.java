package org.oasis_open.contextserver.impl.mergers;

import org.oasis_open.contextserver.api.PropertyMergeStrategyExecutor;
import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.User;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class MostRecentPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public User mergeProperty(String propertyName, PropertyType propertyType, List<User> usersToMerge, User targetUser) {
        return targetUser;
    }
}
