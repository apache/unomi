package org.oasis_open.contextserver.rest;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by toto on 14/01/15.
 */
public class RESTParameter {
    String id;
    String type;
    boolean multivalued = false;
    String defaultValue = null;
    List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMultivalued() {
        return multivalued;
    }

    public void setMultivalued(boolean multivalued) {
        this.multivalued = multivalued;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<ChoiceListValue> getChoiceListValues() {
        return choiceListValues;
    }

    public void setChoiceListValues(List<ChoiceListValue> choiceListValues) {
        this.choiceListValues = choiceListValues;
    }
}
