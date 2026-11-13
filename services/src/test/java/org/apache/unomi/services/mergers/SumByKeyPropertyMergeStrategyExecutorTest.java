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
package org.apache.unomi.services.mergers;

import org.apache.unomi.api.Profile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SumByKeyPropertyMergeStrategyExecutorTest {

    private final SumByKeyPropertyMergeStrategyExecutor executor = new SumByKeyPropertyMergeStrategyExecutor();

    @Test
    public void sumsPointsThatShareAKeyAndKeepsKeysThatAppearOnOnlyOneProfile() {
        Profile master = profile(interests(entry("funds", 30)));
        Profile other = profile(interests(entry("funds", 5), entry("pension", 7)));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(interests(entry("funds", 35), entry("pension", 7)), master.getProperty("interests"));
        assertEquals(interests(entry("funds", 5), entry("pension", 7)), other.getProperty("interests"));
    }

    @Test
    public void copiesInterestsOntoAProfileThatHasNone() {
        Profile master = profile(null);
        Profile other = profile(interests(entry("funds", 5)));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(interests(entry("funds", 5)), master.getProperty("interests"));
    }

    @Test
    public void leavesTheMasterUntouchedWhenNothingIsAdded() {
        List<Map<String, Object>> original = interests(entry("funds", 30));
        Profile master = profile(original);
        Profile other = profile(null);

        assertFalse(executor.mergeProperty("interests", null, Collections.singletonList(other), master));
        assertSame(original, master.getProperty("interests"));
    }

    @Test
    public void sumsTheSameKeyAcrossSeveralProfiles() {
        Profile master = profile(interests(entry("funds", 1)));
        Profile first = profile(interests(entry("funds", 2)));
        Profile second = profile(interests(entry("funds", 3)));

        assertTrue(executor.mergeProperty("interests", null, Arrays.asList(first, second), master));

        assertEquals(interests(entry("funds", 6)), master.getProperty("interests"));
    }

    @Test
    public void sumsDuplicateKeysInsideOneList() {
        Profile master = profile(interests(entry("funds", 1), entry("funds", 2)));
        Profile other = profile(interests(entry("funds", 3)));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(interests(entry("funds", 6)), master.getProperty("interests"));
    }

    @Test
    public void keepsAFractionalScoreAsADouble() {
        Profile master = profile(interests(entry("funds", 1.5d)));
        Profile other = profile(interests(entry("funds", 2)));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(interests(entry("funds", 3.5d)), master.getProperty("interests"));
    }

    @Test
    public void leavesAValueThatIsNotAListUnchanged() {
        Profile master = profile("funds");
        Profile other = profile(interests(entry("funds", 5)));

        assertFalse(executor.mergeProperty("interests", null, Collections.singletonList(other), master));
        assertEquals("funds", master.getProperty("interests"));
    }

    @Test
    public void keepsEntriesThatCannotBeSummed() {
        Map<String, Object> missingKey = new LinkedHashMap<>();
        missingKey.put("value", 9);
        Map<String, Object> missingNumber = new LinkedHashMap<>();
        missingNumber.put("key", "pension");
        missingNumber.put("value", "high");
        Profile master = profile(interests(entry("funds", 1), missingNumber));
        Profile other = profile(new ArrayList<>(Arrays.asList(missingKey, missingNumber, entry("funds", 4))));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(Arrays.asList(entry("funds", 5), missingNumber, missingKey), master.getProperty("interests"));
    }

    @Test
    public void sumsNumericStringValues() {
        Map<String, Object> textValue = new LinkedHashMap<>();
        textValue.put("key", "funds");
        textValue.put("value", " 10 ");
        Map<String, Object> decimalText = new LinkedHashMap<>();
        decimalText.put("key", "pension");
        decimalText.put("value", "0.5");
        Profile master = profile(new ArrayList<>(Arrays.asList(textValue)));
        Profile other = profile(new ArrayList<>(Arrays.asList(entry("funds", 5), decimalText)));

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));

        assertEquals(interests(entry("funds", 15), entry("pension", 0.5d)), master.getProperty("interests"));
    }

    @Test
    public void copiesANonListValueOntoAProfileThatHasNone() {
        Profile master = profile(null);
        Profile other = profile("funds");

        assertTrue(executor.mergeProperty("interests", null, Collections.singletonList(other), master));
        assertEquals("funds", master.getProperty("interests"));
    }

    @Test
    public void prefersAMergedListOverANonListValue() {
        Profile master = profile(null);
        Profile first = profile("funds");
        Profile second = profile(interests(entry("pension", 7)));

        assertTrue(executor.mergeProperty("interests", null, Arrays.asList(first, second), master));
        assertEquals(interests(entry("pension", 7)), master.getProperty("interests"));
    }

    private static Profile profile(Object interests) {
        Profile profile = new Profile("profile-" + System.nanoTime());
        if (interests != null) {
            profile.setProperty("interests", interests);
        }
        return profile;
    }

    @SafeVarargs
    private static List<Map<String, Object>> interests(Map<String, Object>... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }

    private static Map<String, Object> entry(String key, Number value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("value", value);
        return entry;
    }
}
