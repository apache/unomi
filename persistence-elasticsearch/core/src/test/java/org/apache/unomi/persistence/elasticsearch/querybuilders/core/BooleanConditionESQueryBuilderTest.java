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
package org.apache.unomi.persistence.elasticsearch.querybuilders.core;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BooleanConditionESQueryBuilderTest {

    private final BooleanConditionESQueryBuilder builder = new BooleanConditionESQueryBuilder();

    @Mock
    private ConditionESQueryBuilderDispatcher dispatcher;

    @Before
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

        builder.buildQuery(hoverLikeOr(), context, dispatcher);
        ArgumentCaptor<Condition> built = ArgumentCaptor.forClass(Condition.class);
        verify(dispatcher, times(1)).buildFilter(built.capture(), any());
        assertEquals("target.itemId", built.getValue().getParameter("propertyName"));
        assertEquals("button-a", built.getValue().getParameter("propertyValue"));
    }

    @Test
    public void and_allOptionalsUnset_returnsMatchAll() {
        Condition and = new Condition();
        and.setParameter("operator", "and");
        and.setParameter("subConditions", Arrays.asList(
                optionalEquals("target.itemId", "targetId"),
                optionalEquals("target.properties.pageInfo.pagePath", "targetPath")));

        assertTrue(builder.buildQuery(and, new HashMap<>(), dispatcher).isMatchAll());
        verify(dispatcher, never()).buildFilter(any(Condition.class), any());
    }

    @Test
    public void or_emptySubConditions_returnsMatchNone() {
        Condition or = new Condition();
        or.setParameter("operator", "or");
        or.setParameter("subConditions", Collections.emptyList());

        assertTrue(builder.buildQuery(or, new HashMap<>(), dispatcher).isMatchNone());
    }

    @Test
    public void and_unresolvableBranch_returnsMatchNone() {
        Condition and = new Condition();
        and.setParameter("operator", "and");
        and.setParameter("subConditions", Arrays.asList(literalEquals("eventType", "hover"), unresolvable()));

        assertTrue(builder.buildQuery(and, new HashMap<>(), dispatcher).isMatchNone());
        verify(dispatcher, never()).buildFilter(any(Condition.class), any());
    }

    @Test
    public void or_unresolvableBranch_isSkipped() {
        Condition literal = literalEquals("eventType", "hover");
        Condition or = new Condition();
        or.setParameter("operator", "or");
        or.setParameter("subConditions", Arrays.asList(unresolvable(), literal));

        builder.buildQuery(or, new HashMap<>(), dispatcher);
        ArgumentCaptor<Condition> built = ArgumentCaptor.forClass(Condition.class);
        verify(dispatcher, times(1)).buildFilter(built.capture(), any());
        assertEquals(literal, built.getValue());
    }

    @Test
    public void and_unsetOptional_omitsBranchAndKeepsRequired() {
        Condition required = literalEquals("eventType", "hover");
        Condition optional = optionalEquals("target.itemId", "targetId");

        Condition and = new Condition();
        and.setParameter("operator", "and");
        and.setParameter("subConditions", Arrays.asList(required, optional));

        builder.buildQuery(and, new HashMap<>(), dispatcher);
        verify(dispatcher, times(1)).buildFilter(eq(required), any());
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

    private static Condition literalEquals(String propertyName, String value) {
        Condition c = new Condition();
        c.setParameter("propertyName", propertyName);
        c.setParameter("propertyValue", value);
        c.setParameter("comparisonOperator", "equals");
        return c;
    }

    // A script that cannot run (no script executor is set) makes the condition unresolvable.
    private static Condition unresolvable() {
        Condition c = new Condition();
        c.setParameter("propertyValue", "script::anything");
        return c;
    }

    private static Condition optionalEquals(String propertyName, String parameterName) {
        Condition c = new Condition();
        c.setParameter("propertyName", propertyName);
        c.setParameter("propertyValue", "parameter::" + parameterName);
        c.setParameter("comparisonOperator", "equals");
        return c;
    }
}
