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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
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

    // --- helper ---

    private Condition booleanCondition(String operator, Condition... subs) {
        Condition c = new Condition();
        c.setParameter("operator", operator);
        c.setParameter("subConditions", subs.length == 0 ? Collections.emptyList() : Arrays.asList(subs));
        return c;
    }
}
