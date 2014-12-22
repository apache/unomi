package org.oasis_open.contextserver.impl.mergers;

import org.oasis_open.contextserver.api.PropertyMergeStrategyExecutor;
import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.Profile;

import java.util.List;

/**
 * Created by loom on 16.10.14.
 */
public class OldestPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    public Profile mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        return null;
    }
}
