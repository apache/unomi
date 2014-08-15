package org.oasis_open.wemi.context.server.api.conditions;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;

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
    String name;
    String description;
    String type;
    boolean multivalued = false;
    String choicelistInitializerFilter;
    List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();

    public Parameter() {
    }

    public Parameter(String id, String name, String description, String type, boolean multivalued, String choicelistInitializerFilter) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.multivalued = multivalued;
        this.choicelistInitializerFilter = choicelistInitializerFilter;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
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
}
