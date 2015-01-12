package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Initializer for the set of available profile properties.
 */
public class ProfilePropertyTypeChoiceListInitializer implements ChoiceListInitializer {

    ProfileService profileService;

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        Set<PropertyType> profileProperties = profileService.getPropertyTypes("profileProperties", true);
        List<ChoiceListValue> choiceListValues = new ArrayList<>(profileProperties.size());
        for (PropertyType propertyType : profileProperties) {
            String resourceKey = "PROFILE_PROPERTIES_" + propertyType.getId().toUpperCase().replaceAll("\\.", "_")
                    + "_LABEL";
            choiceListValues.add(new PropertyTypeChoiceListValue("properties." + propertyType.getId(), resourceKey,
                    propertyType.getValueTypeId()));
        }
        return choiceListValues;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }
}
