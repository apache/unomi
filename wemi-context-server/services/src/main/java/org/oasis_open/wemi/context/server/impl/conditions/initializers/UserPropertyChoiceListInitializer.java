package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.UserService;
import java.util.ArrayList;
import java.util.List;

/**
 * @todo we should integrate resource bundles for property names (and possibly add descriptions to choice list values)
 */
public class UserPropertyChoiceListInitializer implements ChoiceListInitializer {

    UserService userService;

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        List<String> userProperties = userService.getUserProperties();
        for (String userProperty : userProperties) {
            choiceListValues.add(new ChoiceListValue(userProperty, userProperty));
        }
        return choiceListValues;
    }
}
