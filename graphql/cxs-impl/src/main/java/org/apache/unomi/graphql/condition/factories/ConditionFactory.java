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

package org.apache.unomi.graphql.condition.factories;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.utils.ConditionBuilder;
import org.apache.unomi.graphql.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConditionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionFactory.class);

    protected DataFetchingEnvironment environment;

    protected DefinitionsService definitionsService;

    protected String conditionTypeId;

    private Map<String, ConditionType> conditionTypesMap;

    public ConditionFactory(final String conditionTypeId, final DataFetchingEnvironment environment) {
        this.environment = environment;
        this.conditionTypeId = conditionTypeId;

        final ServiceManager context = environment.getContext();
        this.definitionsService = context.getService(DefinitionsService.class);

        this.conditionTypesMap = definitionsService.getAllConditionTypes().stream()
                .collect(Collectors.toMap(ConditionType::getItemId, Function.identity()));
    }

    public Condition matchAllCondition() {
        return ConditionBuilder.create(getConditionType("matchAllCondition")).build();
    }

    public Condition booleanCondition(final String operator, List<Condition> subConditions) {
        return ConditionBuilder.create(getConditionType("booleanCondition"))
                .parameter("operator", operator)
                .parameter("subConditions", subConditions)
                .build();
    }

    public Condition propertyCondition(final String propertyName, final String operator, final String propertyValueName, final Object propertyValue) {
        return ConditionBuilder.create(getConditionType(conditionTypeId))
                .property(propertyName)
                .operator(operator)
                .parameter(propertyValueName, propertyValue)
                .build();
    }

    public Condition propertyCondition(final String propertyName, final Object propertyValue) {
        return propertyCondition(propertyName, "equals", propertyValue);
    }

    public Condition propertyCondition(final String propertyName, final String operator, final Object propertyValue) {
        return propertyCondition(propertyName, operator, "propertyValue", propertyValue);
    }

    public Condition numberPropertyCondition(final String propertyName, final Object propertyValue) {
        return numberPropertyCondition(propertyName, "equals", propertyValue);
    }

    public Condition numberPropertyCondition(final String propertyName, final String operator, final Object propertyValue) {
        if (propertyValue instanceof Integer || propertyValue instanceof Long) {
            return propertyCondition(propertyName, operator, "propertyValueInteger", propertyValue);
        } else if (propertyValue instanceof Double) {
            return propertyCondition(propertyName, operator, "propertyValueDouble", propertyValue);
        } else {
            return propertyCondition(propertyName, operator, propertyValue);
        }
    }

    public Condition datePropertyCondition(final String propertyName, final String operator, final Object propertyValue) {
        Object processedValue = propertyValue;

        if (propertyValue != null) {
            if (propertyValue instanceof OffsetDateTime) {
                // Convert OffsetDateTime to Date
                processedValue = DateUtils.toDate((OffsetDateTime) propertyValue);
                LOGGER.debug("Converted OffsetDateTime to Date for property {}: {} -> {}",
                    propertyName, propertyValue, processedValue);
            } else if (propertyValue instanceof Date) {
                // Already a Date object, use as is
                LOGGER.debug("Using Date object as is for property {}: {}", propertyName, propertyValue);
            } else {
                // Invalid value type, log warning
                LOGGER.warn("Invalid value type for date property condition. Property: {}, Value: {}, Type: {}. Expected OffsetDateTime or Date.",
                    propertyName, propertyValue, propertyValue.getClass().getSimpleName());
            }
        }

        return propertyCondition(propertyName, operator, "propertyValueDate", processedValue);
    }

    public Condition propertiesCondition(final String propertyName, final String operator, final List<String> propertyValues) {
        return propertyCondition(propertyName, operator, "propertyValues", propertyValues);
    }

    public ConditionType getConditionType(final String typeId) {
        return this.conditionTypesMap.get(typeId);
    }

    public <INPUT> Condition filtersToCondition(
            final List<INPUT> inputFilters, final Function<INPUT, Condition> function, final String operator) {
        final List<Condition> subConditions = inputFilters.stream()
                .map(function)
                .collect(Collectors.toList());

        return booleanCondition(operator, subConditions);
    }

    public <INPUT> Condition filtersToCondition(
            final List<INPUT> inputFilters,
            final List<Map<String, Object>> filterInputAsMap,
            final BiFunction<INPUT, Map<String, Object>, Condition> function,
            final String operator) {
        final List<Condition> subConditions = new ArrayList<>();

        for (int i = 0; i < inputFilters.size(); i++) {
            subConditions.add(function.apply(inputFilters.get(i), filterInputAsMap.get(i)));
        }

        return booleanCondition(operator, subConditions);
    }
}
