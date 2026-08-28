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
package org.apache.unomi.persistence.opensearch.querybuilders.core;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.opensearch.ConditionOSQueryBuilderDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PropertyConditionOSQueryBuilderTest {

    @Mock
    private ConditionOSQueryBuilderDispatcher dispatcher;

    @Test
    void buildQuery_inOperatorWithNullValue_throwsIllegalArgumentException() {
        Condition condition = new Condition();
        condition.setParameter("comparisonOperator", "in");
        condition.setParameter("propertyName", "properties.firstName");
        condition.setParameter("propertyValues", Arrays.asList("Jane", null));

        PropertyConditionOSQueryBuilder builder = new PropertyConditionOSQueryBuilder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> builder.buildQuery(condition, Collections.emptyMap(), dispatcher));

        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    void buildQuery_equalsWithMissingValue_throwsIllegalArgumentException() {
        Condition condition = new Condition();
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyName", "properties.firstName");

        PropertyConditionOSQueryBuilder builder = new PropertyConditionOSQueryBuilder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> builder.buildQuery(condition, Collections.emptyMap(), dispatcher));

        assertEquals("Impossible to build OS filter, missing value for condition using comparisonOperator: equals, and propertyName: properties.firstName",
            exception.getMessage());
    }
}
