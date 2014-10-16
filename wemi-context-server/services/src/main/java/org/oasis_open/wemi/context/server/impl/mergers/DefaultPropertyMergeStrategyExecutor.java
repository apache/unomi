package org.oasis_open.wemi.context.server.impl.mergers;

import org.oasis_open.wemi.context.server.api.PropertyMergeStrategyExecutor;
import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.User;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class DefaultPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public User mergeProperty(String propertyName, PropertyType propertyType, List<User> usersToMerge, User targetUser) {
        for (User userToMerge : usersToMerge) {
            if (userToMerge.getProperty(propertyName) != null && userToMerge.getProperty(propertyName).toString().length() > 0 && targetUser.getProperty(propertyName) == null) {
                targetUser.setProperty(propertyName, userToMerge.getProperty(propertyName));
            }
        }
        return targetUser;
    }
}
