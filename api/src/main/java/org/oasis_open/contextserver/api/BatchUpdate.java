package org.oasis_open.contextserver.api;

import org.oasis_open.contextserver.api.conditions.Condition;

public class BatchUpdate {
    private String propertyName;
    private Object propertyValue;
    private Condition condition;
    private String strategy;

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public Object getPropertyValue() {
        return propertyValue;
    }

    public void setPropertyValue(Object propertyValue) {
        this.propertyValue = propertyValue;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
