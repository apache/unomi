package org.oasis_open.contextserver.api;

import org.oasis_open.contextserver.api.conditions.Condition;

/**
 * A representation of an operation to update the value of a property on items matching a specific condition.
 */
public class BatchUpdate {
    private String propertyName;
    private Object propertyValue;
    private Condition condition;
    private String strategy;

    /**
     * Retrieves the property name which value needs to be updated. Note that the property name follows the
     * <a href='https://commons.apache.org/proper/commons-beanutils/apidocs/org/apache/commons/beanutils/expression/DefaultResolver.html'>Apache Commons BeanUtils expression
     * format</a>
     *
     * @return an Apache Commons BeanUtils expression identifying which property we want to update
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Specifies (as an Apache Commons BeanUtils expression) which property needs to be updated.
     *
     * @param propertyName an Apache Commons BeanUtils expression identifying which property we want to update
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * Retrieves the new property value.
     *
     * @return the new property value
     */
    public Object getPropertyValue() {
        return propertyValue;
    }

    /**
     * Sets the new property value to use for the update.
     *
     * @param propertyValue the new property value to use for the update
     */
    public void setPropertyValue(Object propertyValue) {
        this.propertyValue = propertyValue;
    }

    /**
     * Retrieves the condition which items we want to update must satisfy.
     *
     * @return the condition which items we want to update must satisfy
     */
    public Condition getCondition() {
        return condition;
    }

    /**
     * Specifies the condition which items to update.
     *
     * @param condition the condition specifying which items to update
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    /**
     * Retrieves the identifier for the {@link PropertyMergeStrategyType} to use during the update if needed.
     *
     * @return the identifier for the {@link PropertyMergeStrategyType} to use during the update if needed
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * Sets the identifier for the {@link PropertyMergeStrategyType} to use during the update if needed.
     *
     * @param strategy the identifier for the {@link PropertyMergeStrategyType} to use during the update if needed
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
