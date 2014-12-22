package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.PropertyType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class ProfilePropertyTypeChoiceListInitializer implements ChoiceListInitializer {

    ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        Set<PropertyType> profileProperties = profileService.getPropertyTypes("profileProperties", true);
        for (PropertyType propertyType : profileProperties) {
            String resourceKey = "PROFILE_PROPERTIES_" + propertyType.getId().toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new ChoiceListValue("properties." + propertyType.getId(), resourceKey));
        }
        return choiceListValues;
    }
}
