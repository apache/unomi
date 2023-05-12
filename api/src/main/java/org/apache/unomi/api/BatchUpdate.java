/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api;

import org.apache.unomi.api.conditions.Condition;

/**
 * A representation of an operation to update the value of a property on items matching a specific condition.
 */
public class BatchUpdate {
    private String propertyName;
    private Object propertyValue;
    private Condition condition;
    private String strategy;
    private String scrollTimeValidity = "10m";
    private int scrollBatchSize = 1000;

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

    /**
     * Batch update will perform scroll queries to query document to be updated, the scroll time validity allow specifying
     * how much time the scroll context should stay open in memory to be able to complete the update.
     *
     * @return the scroll time validity (default: 10m)
     */
    public String getScrollTimeValidity() {
        return scrollTimeValidity;
    }

    /**
     * Batch update will perform scroll queries to query document to be updated, the scroll time validity allow specifying
     * how much time the scroll context should stay open in memory to be able to complete the update.
     *
     * @param scrollTimeValidity the scroll time validity in time unit
     */
    public void setScrollTimeValidity(String scrollTimeValidity) {
        this.scrollTimeValidity = scrollTimeValidity;
    }

    /**
     * Batch update will perform scroll queries to query document to be updated, the scroll batch size allow specifying
     * how many document we want to load per scroll.
     *
     * @return the scroll batch size (default: 1000)
     */
    public int getScrollBatchSize() {
        return scrollBatchSize;
    }

    /**
     * Batch update will perform scroll queries to query document to be updated, the scroll batch size allow specifying
     * how many document we want to load per scroll.
     *
     * @param scrollBatchSize the scroll batch size (default: 1000)
     */
    public void setScrollBatchSize(int scrollBatchSize) {
        this.scrollBatchSize = scrollBatchSize;
    }
}
