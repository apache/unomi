package org.apache.unomi.services.mergers;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyMergeStrategyExecutor;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.persistence.spi.PropertyHelper;

import java.util.*;

public class UniqueElementListPropertyMergeStrategyExecutor implements PropertyMergeStrategyExecutor {
    @Override
    public boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile) {
        // sort profiles according to the lastVisit
        profilesToMerge.sort(new LastVisitComparator());

        boolean modified = false;
        List<Object> list = new ArrayList<>(convertObjectToList(targetProfile.getNestedProperty(propertyName)));
        for(Profile profileToMerge: profilesToMerge) {
            if (profileToMerge.getNestedProperty(propertyName) != null && profileToMerge.getNestedProperty(propertyName).toString().length() > 0) {
                List<?> x = convertObjectToList(profileToMerge.getNestedProperty(propertyName));
                for (Object obj: x) {
                    if (!list.contains(obj)) {
                        list.add(obj);
                        modified = true;
                    }
                }
            }
        }
        if (list.size() > 50) {
            list = list.subList(list.size() - 50, list.size());
        }
        PropertyHelper.setProperty(targetProfile, "properties." + propertyName, list, "alwaysSet");
        return modified;
    }

    public static List<?> convertObjectToList(Object obj) {
        List<?> list = new ArrayList<>();
        if (obj instanceof Collection) {
            list = new ArrayList<>((Collection<?>)obj);
        }
        return list;
    }

    private static class LastVisitComparator implements Comparator<Profile> {
        @Override
        public int compare(Profile o1, Profile o2) {
            return o1.getNestedProperty("lastVisit").toString().compareTo(o2.getNestedProperty("lastVisit").toString());
        }
    }
}