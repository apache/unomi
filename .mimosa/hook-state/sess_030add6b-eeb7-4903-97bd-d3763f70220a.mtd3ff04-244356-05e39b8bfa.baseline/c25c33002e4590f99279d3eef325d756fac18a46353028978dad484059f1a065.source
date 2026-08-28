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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.*;

public class NestedConditionEvaluatorTest {
    private final NestedConditionEvaluator nestedConditionEvaluator = new NestedConditionEvaluator();

    @Test
    public void testFlattenedNestedItem_singleLevel() {
        Map<String, Object> result = nestedConditionEvaluator.flattenNestedItem("properties.interests", buildNestedInterest("football", 15));
        Map<String, Object> mergeResultProperties = (Map<String, Object>) result.get("interests");

        assertEquals("The interest should have been flattened in the profile", "football", mergeResultProperties.get("key"));
        assertEquals("The interest should have been flattened in the profile", 15, mergeResultProperties.get("value"));
    }

    @Test
    public void testFlattenedNestedItem_multipleLevel() {
        Map<String, Object> result = nestedConditionEvaluator.flattenNestedItem("properties.subLevel.subLevel2.interests", buildNestedInterest("football", 15));
        Map<String, Object> subLevelResult = (Map<String, Object>) result.get("subLevel");
        Map<String, Object> subLevel2Result = (Map<String, Object>) subLevelResult.get("subLevel2");
        Map<String, Object> interestsResult = (Map<String, Object>) subLevel2Result.get("interests");

        assertEquals("The interest should have been flattened in the profile", "football", interestsResult.get("key"));
        assertEquals("The interest should have been flattened in the profile", 15, interestsResult.get("value"));
    }

    @Test
    public void testFlattenedNestedItem_invalidPaths() {
        assertTrue(nestedConditionEvaluator.flattenNestedItem("properties.", buildNestedInterest("football", 15)).isEmpty());
        assertTrue(nestedConditionEvaluator.flattenNestedItem("", buildNestedInterest("football", 15)).isEmpty());
        assertTrue(nestedConditionEvaluator.flattenNestedItem(null, buildNestedInterest("football", 15)).isEmpty());
    }

    @Test
    public void testCreateFinalNestedItem_Profile() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("foo", "bar");
        
        Profile profile = (Profile) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Profile(), "properties.test", testMap);
        assertEquals("bar", profile.getProperties().get("foo"));
        assertTrue(profile.getSystemProperties().isEmpty());

        profile = (Profile) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Profile(), "systemProperties.test", testMap);
        assertEquals("bar", profile.getSystemProperties().get("foo"));
        assertTrue(profile.getProperties().isEmpty());

        profile = (Profile) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Profile(), "invalidPath", testMap);
        assertTrue(profile.getSystemProperties().isEmpty());
        assertTrue(profile.getProperties().isEmpty());
    }

    @Test
    public void testCreateFinalNestedItem_Session() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("foo", "bar");

        Session session = (Session) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Session(), "properties.test", testMap);
        assertEquals("bar", session.getProperties().get("foo"));
        assertTrue(session.getSystemProperties().isEmpty());

        session = (Session) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Session(), "systemProperties.test", testMap);
        assertEquals("bar", session.getSystemProperties().get("foo"));
        assertTrue(session.getProperties().isEmpty());

        session = (Session) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Session(), "invalidPath", testMap);
        assertTrue(session.getSystemProperties().isEmpty());
        assertTrue(session.getProperties().isEmpty());
    }

    @Test
    public void testCreateFinalNestedItem_unsupportedType() {
        Map<String, Object> testMap = new HashMap<>();
        testMap.put("foo", "bar");

        Event segment = (Event) nestedConditionEvaluator.createFinalNestedItemForEvaluation(new Event(), "properties.test", testMap);
        assertNull(segment);
    }

    private Map<String, Object> buildNestedInterest(String key, Object value) {
        Map<String, Object> nestedInterest = new HashMap<>();
        nestedInterest.put("key", key);
        nestedInterest.put("value", value);
        return nestedInterest;
    }
}
