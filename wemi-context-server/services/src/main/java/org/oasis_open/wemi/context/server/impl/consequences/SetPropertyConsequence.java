package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyConsequence implements Consequence {
    private String propertyName;
    private String propertyValue;

    public SetPropertyConsequence() {
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    @Override
    public boolean apply(User user) {
        user.setProperty(propertyName, propertyValue);
        return true;
    }
}
