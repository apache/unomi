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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BooleanConditionOSQueryBuilderTest {

    private final BooleanConditionOSQueryBuilder builder = new BooleanConditionOSQueryBuilder();

    @Mock
    private ConditionOSQueryBuilderDispatcher dispatcher;

    @BeforeEach
    public void setUp() {
        when(dispatcher.buildFilter(any(Condition.class), any())).thenReturn(Query.of(q -> q.matchAll(m -> m)));
    }

    @Test
    public void or_neitherOptionalSet_returnsMatchNone() {
        Query query = builder.buildQuery(hoverLikeOr(), new HashMap<>(), dispatcher);
        assertTrue(query.isMatchNone());
        verify(dispatcher, never()).buildFilter(any(Condition.class), any());
    }

    @Test
    public void or_onlyTargetIdSet_buildsSingleBranch() {
        Map<String, Object> context = new HashMap<>();
        context.put("targetId", "button-a");

        Query query = builder.buildQuery(hoverLikeOr(), context, dispatcher);
        assertTrue(query.isMatchAll());
        verify(dispatcher, times(1)).buildFilter(any(Condition.class), any());
    }

    @Test
    public void and_unsetOptional_omitsBranchAndKeepsRequired() {
        Condition required = new Condition();
        required.setParameter("propertyName", "eventType");
        required.setParameter("propertyValue", "hover");
        required.setParameter("comparisonOperator", "equals");
        Condition optional = optionalEquals("target.itemId", "targetId");

        Condition and = new Condition();
        and.setParameter("operator", "and");
        and.setParameter("subConditions", Arrays.asList(required, optional));

        Query query = builder.buildQuery(and, new HashMap<>(), dispatcher);
        assertTrue(query.isMatchAll());
        verify(dispatcher, times(1)).buildFilter(any(Condition.class), any());
    }

    private static Condition hoverLikeOr() {
        Condition or = new Condition();
        or.setParameter("operator", "or");
        or.setParameter("subConditions", Arrays.asList(
                optionalEquals("target.itemId", "targetId"),
                optionalEquals("target.properties.pageInfo.pagePath", "targetPath")));
        return or;
    }

    private static Condition optionalEquals(String propertyName, String parameterName) {
        Condition c = new Condition();
        c.setParameter("propertyName", propertyName);
        c.setParameter("propertyValue", "parameter::" + parameterName);
        c.setParameter("comparisonOperator", "equals");
        return c;
    }
}
