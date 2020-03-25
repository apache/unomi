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
package org.apache.unomi.graphql.fetchers;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.schema.PropertyValueTypeHelper;
import org.apache.unomi.graphql.types.output.CDPProfilePropertiesFilter;
import org.apache.unomi.graphql.utils.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CDPProfilePropertiesFilterDataFetcher implements DataFetcher<Object> {

    private final String fieldName;

    private final String propertyName;

    private final String comparisonOperator;

    public CDPProfilePropertiesFilterDataFetcher(final String fieldName) {
        this.fieldName = fieldName;

        final String[] splittedValues = fieldName.split("_", -1);

        this.propertyName = splittedValues[0];
        this.comparisonOperator = splittedValues[1];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(DataFetchingEnvironment environment) throws Exception {
        final Optional<Condition> propertiesCondition = getSubConditions(environment).stream()
                .filter(condition -> "booleanCondition".equals(condition.getConditionTypeId())
                        && "and".equals(condition.getParameter("operator"))
                        && Objects.nonNull(condition.getParameter("subConditions")))
                .flatMap(condition -> ((ArrayList<Condition>) condition.getParameter("subConditions")).stream())
                .filter(condition -> "profilePropertyCondition".equals(condition.getConditionTypeId())
                        && Objects.nonNull(condition.getParameter("propertyName"))
                        && condition.getParameter("propertyName").toString().startsWith("properties." + propertyName)
                        && condition.getParameter("comparisonOperator").toString().equals(comparisonOperator))
                .findFirst();

        final String propertyValueType =
                PropertyValueTypeHelper.getPropertyValueParameter(CDPProfilePropertiesFilter.TYPE_NAME, fieldName, environment);

        return propertiesCondition.map(condition -> {
            if (propertyValueType.equals("propertyValueDate")) {
                return DateUtils.offsetDateTimeFromMap((Map<String, Object>) condition.getParameter(propertyValueType));
            }
            return condition.getParameter(propertyValueType);
        }).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Condition> getSubConditions(final DataFetchingEnvironment environment) {
        final CDPProfilePropertiesFilter source = environment.getSource();

        final List<Condition> subConditions = (List<Condition>) source.getSegmentCondition().getParameter("subConditions");

        if (subConditions == null || subConditions.isEmpty()) {
            return Collections.emptyList();
        }

        return subConditions;
    }

}
