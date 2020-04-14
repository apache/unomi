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
package org.apache.unomi.graphql.utils;

import com.google.common.base.Strings;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConditionBuilder {

    private final ConditionType conditionType;

    private String propertyName;

    private String comparisonOperator;

    private String propertyValue;

    private List<String> propertyValues;

    private OffsetDateTime propertyValueDate;

    private Object propertyValueInteger;

    private Map<String, Object> parameters = new HashMap<>();

    private ConditionBuilder(final ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public static ConditionBuilder create(final ConditionType conditionType) {
        return new ConditionBuilder(conditionType);
    }

    public ConditionBuilder property(String propertyName) {
        this.propertyName = propertyName;
        return this;
    }

    public ConditionBuilder operator(String comparisonOperator) {
        this.comparisonOperator = comparisonOperator;
        return this;
    }

    public ConditionBuilder value(String propertyValue) {
        this.propertyValue = propertyValue;
        return this;
    }

    public ConditionBuilder value(OffsetDateTime propertyValueDate) {
        this.propertyValueDate = propertyValueDate;
        return this;
    }

    public ConditionBuilder value(Object propertyValueInteger) {
        this.propertyValueInteger = propertyValueInteger;
        return this;
    }

    public ConditionBuilder values(List<String> propertyValues) {
        this.propertyValues = propertyValues;
        return this;
    }

    public ConditionBuilder parameter(final String parameter, final Object value) {
        this.parameters.put(parameter, value);
        return this;
    }

    public ConditionBuilder eq(final String value) {
        return this.value(value).operator("equals");
    }

    public ConditionBuilder neq(final String value) {
        return this.value(value).operator("notEquals");
    }

    public ConditionBuilder lt(final String value) {
        return this.value(value).operator("lessThan");
    }

    public ConditionBuilder lte(final String value) {
        return this.value(value).operator("lessThanOrEqualTo");
    }

    public ConditionBuilder gt(final String value) {
        return this.value(value).operator("greaterThan");
    }

    public ConditionBuilder gte(final String value) {
        return this.value(value).operator("greaterThanOrEqualTo");
    }

    public Condition build() {
        final Condition condition = new Condition(conditionType);

        if (!Strings.isNullOrEmpty(propertyName)) {
            condition.setParameter("propertyName", propertyName);
        }
        if (!Strings.isNullOrEmpty(comparisonOperator)) {
            condition.setParameter("comparisonOperator", comparisonOperator);
        }
        if (!Strings.isNullOrEmpty(propertyValue)) {
            condition.setParameter("propertyValue", propertyValue);
        }
        if (propertyValueDate != null) {
            condition.setParameter("propertyValueDate", propertyValueDate);
        }
        if (propertyValueInteger != null) {
            condition.setParameter("propertyValueInteger", propertyValueInteger);
        }
        if (propertyValues != null && !propertyValues.isEmpty()) {
            condition.setParameter("propertyValues", propertyValues);
        }

        if (!parameters.isEmpty()) {
            parameters.forEach(condition::setParameter);
        }

        return condition;
    }

}
