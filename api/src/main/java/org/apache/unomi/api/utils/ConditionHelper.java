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
package org.apache.unomi.api.utils;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;

import java.util.List;
import java.util.Set;

/**
 * Utility class for creating various types of {@link Condition} objects.
 * This class provides methods to easily construct conditions used for querying data based on specific criteria.
 */
public class ConditionHelper {

    private DefinitionsService definitionsService;

    /**
     * Constructs a new ConditionHelper with a specified DefinitionsService.
     *
     * @param definitionsService the DefinitionsService to use for obtaining condition types.
     */
    public ConditionHelper(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }


    /**
     * Creates a profile property condition.
     * This condition is used to evaluate specific properties of profiles.
     *
     * @param propertyName the name of the property to check.
     * @param operator     the comparison operator to apply.
     * @param value        the value to compare against the profile property.
     * @param valueKey     the key of the parameter where to store the value. Can be one of
     *                     [propertyValue, propertyValueInteger, propertyValueDouble, propertyValueDate, propertyValueDateExpr, propertyValues, propertyValuesInteger, propertyValuesDouble, propertyValuesDate, propertyValuesDateExpr]
     * @return a new Condition to evaluate the specified profile property.
     */
    public Condition createProfilePropertyCondition(String propertyName, String operator, Object value, String valueKey) {
        Condition condition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", operator);
        condition.setParameter(valueKey, value);
        return condition;
    }

    /**
     * Creates a boolean condition.
     * This condition can combine multiple sub-conditions using a logical operator (e.g., AND, OR).
     *
     * @param operator      the logical operator to use for combining sub-conditions.
     * @param subConditions the list of sub-conditions to combine.
     * @return a new Condition configured with the specified logical operator and sub-conditions.
     */
    public Condition createBooleanCondition(String operator, List<Condition> subConditions) {
        Condition condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", operator);
        condition.setParameter("subConditions", subConditions);
        return condition;
    }

    /**
     * Creates a nested condition.
     * This condition applies another condition to a nested path within a profile or other entity.
     *
     * @param path         the path within the entity to which the condition should apply.
     * @param subCondition the condition to apply at the specified path.
     * @return a new Condition configured to evaluate the specified path with the provided sub-condition.
     */
    public Condition createNestedCondition(String path, Condition subCondition) {
        Condition nestedCondition = new Condition(definitionsService.getConditionType("nestedCondition"));
        nestedCondition.setParameter("path", path);
        nestedCondition.setParameter("subCondition", subCondition);
        return nestedCondition;
    }


    /**
     * Creates a condition to filter profiles based on a set of IDs.
     * This method constructs a condition that specifies which profiles to include or exclude based on their IDs.
     *
     * @param ids         a {@link Set} of String representing the profile IDs to match.
     * @param shouldMatch a boolean indicating whether the condition should match the profiles with the specified IDs or not.
     * @return a new Condition configured to filter profiles based on the specified IDs and matching criteria.
     */
    public Condition createProfileIdsCondition(Set<String> ids, boolean shouldMatch) {
        Condition idsCondition = new Condition();
        idsCondition.setConditionType(definitionsService.getConditionType("idsCondition"));
        idsCondition.setParameter("ids", ids);
        idsCondition.setParameter("match", shouldMatch);
        return idsCondition;
    }

}
