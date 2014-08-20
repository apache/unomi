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
    String nameKey;
    String descriptionKey;
    String type;
    boolean multivalued = false;
    String choicelistInitializerFilter;
    List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();

    public Parameter() {
    }

    public Parameter(String id, String nameKey, String description, String type, boolean multivalued, String choicelistInitializerFilter) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = description;
        this.type = type;
        this.multivalued = multivalued;
        this.choicelistInitializerFilter = choicelistInitializerFilter;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        if (nameKey == null) {
            nameKey = id.toUpperCase().replaceAll("\\.", "_") + "_PARAMETER_NAME_LABEL";
        }
        return nameKey;
    }

    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = id.toUpperCase().replaceAll("\\.", "_") + "_PARAMETER_DESCRIPTION_LABEL";
        }
        return descriptionKey;
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
