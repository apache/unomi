package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class UserPropertyTypeChoiceListInitializer implements ChoiceListInitializer {

    UserService userService;

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        Set<PropertyType> userProperties = userService.getPropertyTypes("userProperties", true);
        for (PropertyType propertyType : userProperties) {
            String resourceKey = "USER_PROPERTIES_" + propertyType.getId().toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new ChoiceListValue(propertyType.getId(), resourceKey));
        }
        return choiceListValues;
    }
}
