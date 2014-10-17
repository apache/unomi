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
            if (propertyType.getValueTypeId().equals("integer")) {
                result = new Integer(0);
            } else if (propertyType.getValueTypeId().equals("long")) {
                result = new Long(0);
            } else if (propertyType.getValueTypeId().equals("double")) {
                result = new Double(0.0);
            } else if (propertyType.getValueTypeId().equals("float")) {
                result = new Float(0.0);
            } else {
                result = new Long(0);
            }
        } else {
            result = new Long(0);
        }

        for (User userToMerge : usersToMerge) {

            if (userToMerge.getProperty(propertyName) == null) {
                continue;
            }

            if (propertyType != null) {
                if (propertyType.getValueTypeId().equals("integer") || (userToMerge.getProperty(propertyName) instanceof Integer)) {
                    result = (Integer) result + (Integer) userToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("long") || (userToMerge.getProperty(propertyName) instanceof Long)) {
                    result = (Long) result + (Long) userToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("double") || (userToMerge.getProperty(propertyName) instanceof Double)) {
                    result = (Double) result + (Double) userToMerge.getProperty(propertyName);
                } else if (propertyType.getValueTypeId().equals("float") || (userToMerge.getProperty(propertyName) instanceof Float)) {
                    result = (Float) result + (Float) userToMerge.getProperty(propertyName);
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
