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

@RunWith(MockitoJUnitRunner.class)
public class IdsConditionEvaluatorTest {

    private final IdsConditionEvaluator evaluator = new IdsConditionEvaluator();

    @Mock
    private ConditionEvaluatorDispatcher dispatcher; // never called by IdsConditionEvaluator

    private Profile profile;

    @Before
    public void setUp() {
        profile = new Profile("profile-a");
    }

    // --- null / empty ids always false ---

    @Test
    public void nullIds_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(null, null), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void emptyIds_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(Collections.emptyList(), null), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void emptyIds_matchFalse_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(Collections.emptyList(), false), profile, new HashMap<>(), dispatcher));
    }

    // --- null match defaults to inclusion (match=true semantics, no NPE) ---

    @Test
    public void nullMatch_itemInList_returnsTrue() {
        assertTrue(evaluator.eval(idsCondition(Arrays.asList("profile-a", "profile-b"), null), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void nullMatch_itemNotInList_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(Arrays.asList("profile-x"), null), profile, new HashMap<>(), dispatcher));
    }

    // --- explicit match=true (inclusion mode) ---

    @Test
    public void matchTrue_itemInList_returnsTrue() {
        assertTrue(evaluator.eval(idsCondition(Arrays.asList("profile-a"), true), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void matchTrue_itemNotInList_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(Arrays.asList("profile-x"), true), profile, new HashMap<>(), dispatcher));
    }

    // --- explicit match=false (exclusion mode) ---

    @Test
    public void matchFalse_itemInList_returnsFalse() {
        assertFalse(evaluator.eval(idsCondition(Arrays.asList("profile-a", "profile-b"), false), profile, new HashMap<>(), dispatcher));
    }

    @Test
    public void matchFalse_itemNotInList_returnsTrue() {
        assertTrue(evaluator.eval(idsCondition(Arrays.asList("profile-x", "profile-y"), false), profile, new HashMap<>(), dispatcher));
    }

    // --- helper ---

    private Condition idsCondition(Object ids, Boolean match) {
        Condition c = new Condition();
        c.setParameter("ids", ids);
        c.setParameter("match", match);
        return c;
    }
}
