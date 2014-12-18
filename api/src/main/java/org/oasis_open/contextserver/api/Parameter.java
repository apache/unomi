package org.oasis_open.contextserver.api;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a condition parameter, that will be used in the segment building UI to either select parameters from a
 * choicelist or to enter a specific value.
 */
@XmlRootElement
public class Parameter {

    String id;
    String type;
    boolean multivalued = false;
    String choicelistInitializerFilter;
    List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
    String defaultValue = null;

    public Parameter() {
    }

    public Parameter(String id, String nameKey, String description, String type, boolean multivalued, String choicelistInitializerFilter, String defaultValue) {
        this.id = id;
        this.type = type;
        this.multivalued = multivalued;
        this.choicelistInitializerFilter = choicelistInitializerFilter;
        this.defaultValue = defaultValue;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public boolean isMultivalued() {
        return multivalued;
    }

    public String getChoicelistInitializerFilter() {
        return choicelistInitializerFilter;
    }

    public List<ChoiceListValue> getChoiceListValues() {
        return choiceListValues;
    }

    public void setChoiceListValues(List<ChoiceListValue> choiceListValues) {
        this.choiceListValues = choiceListValues;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
