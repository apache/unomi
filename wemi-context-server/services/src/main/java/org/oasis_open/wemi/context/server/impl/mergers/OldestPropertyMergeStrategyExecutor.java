package org.oasis_open.wemi.context.server.impl.mergers;

import org.oasis_open.wemi.context.server.api.PropertyMergeStrategyExecutor;
import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.User;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class OldestPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public User mergeProperty(String propertyName, PropertyType propertyType, List<User> usersToMerge, User targetUser) {
        return null;
    }
}
