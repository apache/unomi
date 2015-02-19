package org.oasis_open.contextserver.impl.mergers;

import org.oasis_open.contextserver.api.PropertyMergeStrategyExecutor;
import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.Profile;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class DefaultPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        boolean modified = false;
        for (Profile profileToMerge : profilesToMerge) {
            if (profileToMerge.getProperty(propertyName) != null &&
                    profileToMerge.getProperty(propertyName).toString().length() > 0 &&
                    targetProfile.getProperty(propertyName) == null) {
                targetProfile.setProperty(propertyName, profileToMerge.getProperty(propertyName));
                modified = true;
            }
        }
        return modified;
    }
}
