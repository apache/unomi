package org.apache.unomi.services.mergers;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyMergeStrategyExecutor;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.persistence.spi.PropertyHelper;

import java.util.List;

public class SumPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor  {
    @Override
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        boolean modified = false;

        double propertyValue = convertObjectToDouble(targetProfile.getNestedProperty(propertyName));

        for(Profile profileToMerge: profilesToMerge)
            propertyValue += convertObjectToDouble(profileToMerge.getNestedProperty(propertyName));

        PropertyHelper.setProperty(targetProfile, "properties." + propertyName, propertyValue, "alwaysSet");
        return modified;
    }

    public static Double convertObjectToDouble(Object obj) {
        String stringValue = String.valueOf(obj).trim();

        Double doubleValue = ((obj == null || StringUtils.isBlank(stringValue))
                ? 0.0 : Double.parseDouble(stringValue));
        return doubleValue;
    }
}