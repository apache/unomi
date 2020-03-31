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

package org.apache.unomi.graphql.condition;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;

public abstract class ConditionFactory {

    private static ProfileConditionFactory profileConditionFactory = new ProfileConditionFactory();

    private static EventConditionFactory eventConditionFactory = new EventConditionFactory();

    private String entityName;

    protected ConditionFactory(String entityName) {
        this.entityName = entityName;
    }

    public static ProfileConditionFactory profile() {
        return ConditionFactory.profileConditionFactory;
    }

    public static EventConditionFactory event() {
        return ConditionFactory.eventConditionFactory;
    }

    public Condition createBoolCondition(final String operator, DefinitionsService definitionsService) {
        final Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", operator);
        return andCondition;
    }

    public Condition createPropertyCondition(final String propertyName, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, "equals", propertyValue, definitionsService);
    }

    public Condition createPropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValue", propertyValue, definitionsService);
    }

    public Condition createIntegerPropertyCondition(final String propertyName, final Object propertyValue, DefinitionsService definitionsService) {
        return createIntegerPropertyCondition(propertyName, "equals", propertyValue, definitionsService);
    }

    public Condition createIntegerPropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValueInteger", propertyValue, definitionsService);
    }

    public Condition createDatePropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValueDate", propertyValue, definitionsService);
    }

    public Condition createPropertiesCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValues", propertyValue, definitionsService);
    }

    public Condition createPropertyCondition(final String propertyName, final String operator, final String propertyValueName, final Object propertyValue, DefinitionsService definitionsService) {
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType(entityName));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", operator);
        profileIdCondition.setParameter(propertyValueName, propertyValue);

        return profileIdCondition;
    }
}
