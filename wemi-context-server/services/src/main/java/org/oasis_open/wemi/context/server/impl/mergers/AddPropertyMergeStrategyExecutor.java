package org.oasis_open.wemi.context.server.impl.mergers;

import org.oasis_open.wemi.context.server.api.PropertyMergeStrategyExecutor;
import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.User;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class AddPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public User mergeProperty(String propertyName, PropertyType propertyType, List<User> usersToMerge, User targetUser) {

        Object result = null;
        if (propertyType.getValueTypeId() != null) {
            if (propertyType.getValueTypeId().equals("integer") || propertyType.getValueTypeId().equals("long")) {
                result = new Long(0);
            } else if (propertyType.getValueTypeId().equals("double") || propertyType.getValueTypeId().equals("float")) {
                result = new Double(0.0);
            } else {
                result = new Long(0);
            }
        } else {
            result = new Long(0);
        }

        for (User userToMerge : usersToMerge) {

            if (propertyType != null) {
                if (propertyType.getValueTypeId().equals("integer") || propertyType.getValueTypeId().equals("long")) {
                    result = (Long) result + (Long) userToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("double") || propertyType.getValueTypeId().equals("float")) {
                    result = (Double) result + (Double) userToMerge.getProperty(propertyName);
                } else {
                    result = (Long) result + Long.parseLong(userToMerge.getProperty(propertyName).toString());
                }
            } else {
                result = (Long) result + Long.parseLong(userToMerge.getProperty(propertyName).toString());
            }

        }

        targetUser.setProperty(propertyName, result);

        return targetUser;
    }
}
