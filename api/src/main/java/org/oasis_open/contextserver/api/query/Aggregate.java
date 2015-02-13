package org.oasis_open.contextserver.api.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kevan on 12/01/15.
 */
public class Aggregate {
    private String type;
    private String property;
    private Map<String, Object> parameters = new HashMap<>();
    private List<NumericRange> numericRanges = new ArrayList<>();
    private List<GenericRange> genericRanges = new ArrayList<>();

    public Aggregate() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public List<NumericRange> getNumericRanges() {
        return numericRanges;
    }

    public void setNumericRanges(List<NumericRange> numericRanges) {
        this.numericRanges = numericRanges;
    }

    public List<GenericRange> getGenericRanges() {
        return genericRanges;
    }

    public void setGenericRanges(List<GenericRange> genericRanges) {
        this.genericRanges = genericRanges;
    }
}
