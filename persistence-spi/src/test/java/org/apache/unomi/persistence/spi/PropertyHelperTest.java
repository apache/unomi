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
package org.apache.unomi.persistence.spi;

import org.apache.unomi.api.Profile;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class PropertyHelperTest {
    @Test
    public void testStrategy_Remove() {
        Profile profile = new Profile();
        profile.setProperty("test", "test");
        boolean updated = PropertyHelper.setProperty(profile, "properties.test", null, "remove");
        assertNull("Profile property should be removed", profile.getProperty("test"));
        assertTrue("Should return updated", updated);

        // Removing non existing prop should do nothing
        updated = PropertyHelper.setProperty(profile, "properties.test", null, "remove");
        assertNull("Profile property should not exist", profile.getProperty("test"));
        assertFalse("Should return not updated", updated);
    }

    @Test
    public void testStrategy_Null_AlwaysSet_SetIfMissing() {
        Profile profile = new Profile();
        profile.setProperty("test", "test");
        boolean updated = PropertyHelper.setProperty(profile, "properties.test", "test updated", null);
        assertEquals("Profile property should be updated", "test updated", profile.getProperty("test"));
        assertTrue("Should return updated", updated);

        updated = PropertyHelper.setProperty(profile, "properties.test", "test updated 2", "alwaysSet");
        assertEquals("Profile property should be updated", "test updated 2", profile.getProperty("test"));
        assertTrue("Should return updated", updated);

        updated = PropertyHelper.setProperty(profile, "properties.test", "test updated 3", "setIfMissing");
        assertEquals("Profile property should not be updated", "test updated 2", profile.getProperty("test"));
        assertFalse("Should return not updated", updated);

        updated = PropertyHelper.setProperty(profile, "properties.testMissing", "test missing", "setIfMissing");
        assertEquals("Profile property should be updated", "test missing", profile.getProperty("testMissing"));
        assertTrue("Should return updated", updated);
    }

    @Test
    public void testStrategy_AddValue() {
        Profile profile = new Profile();
        profile.setProperty("test", Arrays.asList("value 1"));

        // test add 1 element
        boolean updated = PropertyHelper.setProperty(profile, "properties.test", "value 2", "addValue");
        assertList(profile, "test", Arrays.asList("value 1", "value 2"));
        assertTrue("Should return updated", updated);

        // test element are not added twice
        updated = PropertyHelper.setProperty(profile, "properties.test", "value 2", "addValue");
        assertList(profile, "test", Arrays.asList("value 1", "value 2"));
        assertFalse("Should return not be updated", updated);

        // test add multiple elements
        updated = PropertyHelper.setProperty(profile, "properties.test", Arrays.asList("value 2", "value 3", "value 4"), "addValues");
        assertList(profile, "test", Arrays.asList("value 1", "value 2", "value 3", "value 4"));
        assertTrue("Should return updated", updated);

        // test element are not added twice
        updated = PropertyHelper.setProperty(profile, "properties.test", Arrays.asList("value 2", "value 3", "value 4"), "addValues");
        assertList(profile, "test", Arrays.asList("value 1", "value 2", "value 3", "value 4"));
        assertFalse("Should return not be updated", updated);

        // test add values to non existing prop
        updated = PropertyHelper.setProperty(profile, "properties.newProp", "value 1", "addValue");
        assertList(profile, "newProp", Arrays.asList("value 1"));
        assertTrue("Should return updated", updated);
    }

    @Test
    public void testStrategy_RemoveValue() {
        Profile profile = new Profile();
        profile.setProperty("test", Arrays.asList("value 1", "value 2", "value 3", "value 4", "value 5"));

        // test remove 1 element
        boolean updated = PropertyHelper.setProperty(profile, "properties.test", "value 5", "removeValue");
        assertList(profile, "test", Arrays.asList("value 1", "value 2", "value 3", "value 4"));
        assertTrue("Should return updated", updated);

        // test remove 1 element that doesnt exist in the list
        updated = PropertyHelper.setProperty(profile, "properties.test", "value 5", "removeValue");
        assertList(profile, "test", Arrays.asList("value 1", "value 2", "value 3", "value 4"));
        assertFalse("Should return not be updated", updated);

        // test remove multiple elements
        updated = PropertyHelper.setProperty(profile, "properties.test", Arrays.asList("value 3", "value 4"), "removeValues");
        assertList(profile, "test", Arrays.asList("value 1", "value 2"));
        assertTrue("Should return updated", updated);

        // test remove multiple element that doesnt exist
        updated = PropertyHelper.setProperty(profile, "properties.test", Arrays.asList("value 3", "value 4"), "removeValues");
        assertList(profile, "test", Arrays.asList("value 1", "value 2"));
        assertFalse("Should return not be updated", updated);

        // test remove values to non existing prop
        updated = PropertyHelper.setProperty(profile, "properties.newProp", "value 1", "removeValue");
        assertNull("Profile property should not exist", profile.getProperty("newProp"));
        assertFalse("Should return not updated", updated);
    }

    private void assertList(Profile profile, String propertyName, List<String> expectedList) {
        List<String> currentValue = (List<String>) profile.getProperty(propertyName);
        assertTrue("The list is not containing the expected elements", currentValue.containsAll(expectedList));
        assertEquals("The list size does not match the expected list size", expectedList.size(), currentValue.size());
    }
}
