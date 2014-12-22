package org.oasis_open.contextserver.impl.mergers;

import org.oasis_open.contextserver.api.PropertyMergeStrategyExecutor;
import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.Profile;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class DefaultPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public Profile mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        for (Profile profileToMerge : profilesToMerge) {
            if (profileToMerge.getProperty(propertyName) != null && profileToMerge.getProperty(propertyName).toString().length() > 0 && targetProfile.getProperty(propertyName) == null) {
                targetProfile.setProperty(propertyName, profileToMerge.getProperty(propertyName));
            }
        }
        return targetProfile;
    }
}
