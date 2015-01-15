package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a condition parameter, that will be used in the segment building UI to either select parameters from a
 * choicelist or to enter a specific value.
 */
@XmlRootElement
public class Parameter {

    String id;
    String type;
    boolean multivalued = false;
    String choiceListInitializerFilter;
    String defaultValue = null;

    public Parameter() {
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

    public String getChoiceListInitializerFilter() {
        return choiceListInitializerFilter;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
