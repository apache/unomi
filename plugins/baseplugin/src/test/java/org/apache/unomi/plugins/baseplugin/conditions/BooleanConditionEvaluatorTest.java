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
package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BooleanConditionEvaluatorTest {

    private final BooleanConditionEvaluator evaluator = new BooleanConditionEvaluator();

    @Mock
    private ConditionEvaluatorDispatcher dispatcher;

    private Profile profile;

    @Before
    public void setUp() {
        profile = new Profile("test-profile");
    }

    // --- empty / null subConditions ---

    @Test
    public void emptyAnd_returnsTrue() {
        assertTrue(evaluator.eval(booleanCondition("and"), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void nullSubConditions_and_returnsTrue() {
        Condition c = new Condition();
        c.setParameter("operator", "and");
        assertTrue(evaluator.eval(c, profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void emptyOr_returnsFalse() {
        assertFalse(evaluator.eval(booleanCondition("or"), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void nullSubConditions_or_returnsFalse() {
        Condition c = new Condition();
        c.setParameter("operator", "or");
        assertFalse(evaluator.eval(c, profile, new HashMap<>(), dispatcher));
    }

    // --- AND logic ---

    @Test
    public void and_allTrue_returnsTrue() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(true);
        when(dispatcher.eval(same(sub2), same(profile), any())).thenReturn(true);
        assertTrue(evaluator.eval(booleanCondition("and", sub1, sub2), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void and_firstFalse_returnsFalse() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(false);
        assertFalse(evaluator.eval(booleanCondition("and", sub1, sub2), profile, new HashMap<>(), dispatcher));
        verify(dispatcher, never()).eval(same(sub2), any(), any());
    }

    @Test
    public void and_lastFalse_returnsFalse() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(true);
        when(dispatcher.eval(same(sub2), same(profile), any())).thenReturn(false);
        assertFalse(evaluator.eval(booleanCondition("and", sub1, sub2), profile, new HashMap<>(), dispatcher));
    }

    // --- OR logic ---

    @Test
    public void or_allFalse_returnsFalse() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(false);
        when(dispatcher.eval(same(sub2), same(profile), any())).thenReturn(false);
        assertFalse(evaluator.eval(booleanCondition("or", sub1, sub2), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void or_firstTrue_returnsTrue() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(true);
        assertTrue(evaluator.eval(booleanCondition("or", sub1, sub2), profile, new HashMap<>(), dispatcher));
        verify(dispatcher, never()).eval(same(sub2), any(), any());
    }

    @Test
    public void or_lastTrue_returnsTrue() {
        Condition sub1 = new Condition();
        Condition sub2 = new Condition();
        when(dispatcher.eval(same(sub1), same(profile), any())).thenReturn(false);
        when(dispatcher.eval(same(sub2), same(profile), any())).thenReturn(true);
        assertTrue(evaluator.eval(booleanCondition("or", sub1, sub2), profile, new HashMap<>(), dispatcher));
    }

    // --- operator case-insensitivity ---

    @Test
    public void operatorUppercaseAND_emptySubConditions_returnsTrue() {
        Condition c = new Condition();
        c.setParameter("operator", "AND");
        c.setParameter("subConditions", Collections.emptyList());
        assertTrue(evaluator.eval(c, profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void operatorUppercaseOR_emptySubConditions_returnsFalse() {
        Condition c = new Condition();
        c.setParameter("operator", "OR");
        c.setParameter("subConditions", Collections.emptyList());
        assertFalse(evaluator.eval(c, profile, new HashMap<>(), dispatcher));
    }

    // --- null operator treated as non-"and" (OR semantics) ---

    @Test
    public void nullOperator_emptySubConditions_returnsFalse() {
        Condition c = new Condition();
        c.setParameter("subConditions", Collections.emptyList());
        assertFalse(evaluator.eval(c, profile, new HashMap<>(), dispatcher));
    }

    // --- type safety guard ---

    @Test
    public void nonListSubConditions_throwsIllegalArgumentException() {
        // Passing a non-List as subConditions must throw IAE immediately (not silently cast or swallow).
        Condition c = new Condition();
        c.setParameter("operator", "and");
        c.setParameter("subConditions", "not-a-list");

        try {
            evaluator.eval(c, profile, new HashMap<>(), dispatcher);
            fail("Expected IllegalArgumentException when subConditions is not a List");
        } catch (IllegalArgumentException expected) {
            assertTrue("Exception message must mention 'subConditions'",
                expected.getMessage().contains("subConditions"));
        }
    }

    // --- optional parameters under OR (UNOMI-1004) ---

    @Test
    public void or_unsetOptionalBranch_isOmitted_andDoesNotMatchEverything() {
        Condition targetIdBranch = optionalEquals("target.itemId", "targetId");
        Condition targetPathBranch = optionalEquals("target.properties.pageInfo.pagePath", "targetPath");

        // Only targetId is set, and it does not match — the unset path branch must not make OR true.
        Map<String, Object> context = new HashMap<>();
        context.put("targetId", "button-a");
        when(dispatcher.eval(any(Condition.class), same(profile), any())).thenReturn(false);

        assertFalse(evaluator.eval(booleanCondition("or", targetIdBranch, targetPathBranch),
                profile, context, dispatcher));
        // Only the resolved targetId branch is evaluated; the unset path branch is omitted.
        verify(dispatcher, times(1)).eval(any(Condition.class), same(profile), any());
    }

    @Test
    public void or_onlyTargetIdSet_matchesWhenIdMatches() {
        Condition targetIdBranch = optionalEquals("target.itemId", "targetId");
        Condition targetPathBranch = optionalEquals("target.properties.pageInfo.pagePath", "targetPath");

        Map<String, Object> context = new HashMap<>();
        context.put("targetId", "button-a");
        when(dispatcher.eval(any(Condition.class), same(profile), any())).thenReturn(true);

        assertTrue(evaluator.eval(booleanCondition("or", targetIdBranch, targetPathBranch),
                profile, context, dispatcher));
        verify(dispatcher, times(1)).eval(any(Condition.class), same(profile), any());
    }

    @Test
    public void or_neitherOptionalSet_matchesNothing() {
        Condition targetIdBranch = optionalEquals("target.itemId", "targetId");
        Condition targetPathBranch = optionalEquals("target.properties.pageInfo.pagePath", "targetPath");

        assertFalse(evaluator.eval(booleanCondition("or", targetIdBranch, targetPathBranch),
                profile, new HashMap<>(), dispatcher));
        verify(dispatcher, never()).eval(any(Condition.class), any(), any());
    }

    @Test
    public void and_unsetOptionalBranch_isOmitted_stillMatches() {
        // UNOMI-982: an unset optional under AND states no constraint.
        Condition required = new Condition();
        required.setParameter("propertyName", "eventType");
        required.setParameter("propertyValue", "hover");
        required.setParameter("comparisonOperator", "equals");
        Condition optional = optionalEquals("target.itemId", "targetId");

        when(dispatcher.eval(any(Condition.class), same(profile), any())).thenReturn(true);

        assertTrue(evaluator.eval(booleanCondition("and", required, optional),
                profile, new HashMap<>(), dispatcher));
        verify(dispatcher, times(1)).eval(any(Condition.class), same(profile), any());
    }

    @Test
    public void and_allOptionalsUnset_matches() {
        Condition targetIdBranch = optionalEquals("target.itemId", "targetId");
        Condition targetPathBranch = optionalEquals("target.properties.pageInfo.pagePath", "targetPath");

        assertTrue(evaluator.eval(booleanCondition("and", targetIdBranch, targetPathBranch),
                profile, new HashMap<>(), dispatcher));
        verify(dispatcher, never()).eval(any(Condition.class), any(), any());
    }

    // A script that cannot run (no script executor here) makes the sub-condition unresolvable.

    @Test
    public void and_unresolvableBranch_failsClosed() {
        Condition broken = new Condition();
        broken.setParameter("propertyValue", "script::anything");
        Condition literal = new Condition();
        literal.setParameter("propertyValue", "hover");

        assertFalse(evaluator.eval(booleanCondition("and", literal, broken), profile, new HashMap<>(), dispatcher));
        verify(dispatcher, never()).eval(any(Condition.class), any(), any());
    }

    @Test
    public void or_unresolvableBranch_isSkipped() {
        Condition broken = new Condition();
        broken.setParameter("propertyValue", "script::anything");
        Condition literal = new Condition();
        literal.setParameter("propertyValue", "hover");
        when(dispatcher.eval(same(literal), same(profile), any())).thenReturn(true);

        assertTrue(evaluator.eval(booleanCondition("or", broken, literal), profile, new HashMap<>(), dispatcher));
        verify(dispatcher, times(1)).eval(any(Condition.class), same(profile), any());
    }

    // --- helper ---

    private Condition booleanCondition(String operator, Condition... subs) {
        Condition c = new Condition();
        c.setParameter("operator", operator);
        c.setParameter("subConditions", subs.length == 0 ? Collections.emptyList() : Arrays.asList(subs));
        return c;
    }

    private Condition optionalEquals(String propertyName, String parameterName) {
        Condition c = new Condition();
        c.setParameter("propertyName", propertyName);
        c.setParameter("propertyValue", "parameter::" + parameterName);
        c.setParameter("comparisonOperator", "equals");
        return c;
    }
}
