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
 * limitations under the License
 */

package org.apache.unomi.itests;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.plugins.baseplugin.actions.UpdatePropertiesAction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

/**
 * Created by amidani on 12/10/2017.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PropertiesUpdateActionIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(PropertiesUpdateActionIT.class);

    private final static String PROFILE_TARGET_TEST_ID = "profile-target-event";
    private final static String PROFILE_TEST_ID = "profile-to-update-by-event";

    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;
    @Inject @Filter(timeout = 600000)
    protected EventService eventService;

    @Before
    public void setUp() throws IOException, InterruptedException {
        Profile profile = new Profile();
        profile.setItemId(PROFILE_TEST_ID);
        profileService.save(profile);
        LOGGER.info("Profile saved with ID [{}].", profile.getItemId());

        Profile profileTarget = new Profile();
        profileTarget.setItemId(PROFILE_TARGET_TEST_ID);
        profileService.save(profileTarget);
        LOGGER.info("Profile saved with ID [{}].", profileTarget.getItemId());

        refreshPersistence();
    }

    @Test
    public void testUpdateProperties_CurrentProfile() {
        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Assert.assertNull(profile.getProperty("firstName"));

        Event updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);

        Map<String, Object> propertyToUpdate = new HashMap<>();
        propertyToUpdate.put("properties.firstName", "UPDATED FIRST NAME CURRENT PROFILE");

        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TARGET_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");

        int changes = eventService.send(updateProperties);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertEquals("UPDATED FIRST NAME CURRENT PROFILE", profile.getProperty("firstName"));
    }

    @Test
    public void testUpdateProperties_NotCurrentProfile() {
        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Profile profileToUpdate = profileService.load(PROFILE_TEST_ID);
        Assert.assertNull(profileToUpdate.getProperty("firstName"));

        Event updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);

        Map<String, Object> propertyToUpdate = new HashMap<>();
        propertyToUpdate.put("properties.firstName", "UPDATED FIRST NAME");

        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_UPDATE, propertyToUpdate);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");
        eventService.send(updateProperties);

        profileToUpdate = profileService.load(PROFILE_TEST_ID);
        Assert.assertEquals("UPDATED FIRST NAME", profileToUpdate.getProperty("firstName"));
    }

    @Test
    public void testUpdateProperties_CurrentProfile_PROPS_TO_ADD() throws InterruptedException {
        Profile profile = profileService.load(PROFILE_TEST_ID);

        Event updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);

        Map<String, Object> propertyToAdd = new HashMap<>();
        propertyToAdd.put("properties.prop1", "New property 1");
        propertyToAdd.put("properties.prop2", "New property 2");
        propertyToAdd.put("properties.prop3", "New property 3");

        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_ADD, propertyToAdd);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");
        eventService.send(updateProperties);
        profileService.save(profile);
        refreshPersistence();

        profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertEquals("New property 1", profile.getProperty("prop1"));
        Assert.assertEquals("New property 2", profile.getProperty("prop2"));
        Assert.assertEquals("New property 3", profile.getProperty("prop3"));
    }

    @Test
    public void testUpdateProperties_CurrentProfile_PROPS_TO_ADD_TO_SET() throws InterruptedException {
        Profile profile = profileService.load(PROFILE_TEST_ID);
        Event updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);

        Map<String, Object> propertyToAddToSet = new HashMap<>();
        propertyToAddToSet.put("properties.prop1", "New property 1");
        propertyToAddToSet.put("properties.prop2", "New property 2");
        propertyToAddToSet.put("properties.prop3", "New property 3");

        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_ADD, propertyToAddToSet);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");
        eventService.send(updateProperties);
        profileService.save(profile);
        refreshPersistence();

        profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertEquals("New property 1", profile.getProperty("prop1"));
        Assert.assertEquals("New property 2", profile.getProperty("prop2"));
        Assert.assertEquals("New property 3", profile.getProperty("prop3"));

        // Add set and check
        propertyToAddToSet = new HashMap<>();
        propertyToAddToSet.put("properties.prop1", "New property 1 bis");
        propertyToAddToSet.put("properties.prop3", "New property 3 bis");

        updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);
        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_ADD_TO_SET, propertyToAddToSet);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");
        eventService.send(updateProperties);
        profileService.save(profile);
        refreshPersistence();

        profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertEquals(2, ((List<String>) profile.getProperty("prop1")).size());
        Assert.assertEquals(2, ((List<String>) profile.getProperty("prop3")).size());
        Assert.assertEquals("New property 1", ((List<String>) profile.getProperty("prop1")).get(1));
        Assert.assertEquals("New property 2", profile.getProperty("prop2"));
        Assert.assertEquals("New property 3 bis", ((List<String>) profile.getProperty("prop3")).get(0));
    }

    @Test
    public void testUpdateProperties_CurrentProfile_PROPS_TO_DELETE() throws InterruptedException {
        Profile profile = profileService.load(PROFILE_TEST_ID);
        Event updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);

        Map<String, Object> propertyToAdd = new HashMap<>();
        propertyToAdd.put("properties.prop1", "New property 1");
        propertyToAdd.put("properties.prop1bis", "New property 1 bis");
        propertyToAdd.put("properties.prop2", "New property 2");
        propertyToAdd.put("properties.prop3", "New property 3");

        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_ADD, propertyToAdd);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");
        eventService.send(updateProperties);
        profileService.save(profile);
        refreshPersistence();

        profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertEquals("New property 1", profile.getProperty("prop1"));
        Assert.assertEquals("New property 1 bis", profile.getProperty("prop1bis"));
        Assert.assertEquals("New property 2", profile.getProperty("prop2"));
        Assert.assertEquals("New property 3", profile.getProperty("prop3"));

        // Delete property and check
        List<String> propertyToDelete = new ArrayList<>();
        propertyToDelete.add("properties.prop1bis");

        updateProperties = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updateProperties.setPersistent(false);
        updateProperties.setProperty(UpdatePropertiesAction.PROPS_TO_DELETE, propertyToDelete);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_ID_KEY, PROFILE_TEST_ID);
        updateProperties.setProperty(UpdatePropertiesAction.TARGET_TYPE_KEY, "profile");

        eventService.send(updateProperties);
        profileService.save(profile);
        refreshPersistence();

        profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertNull(profile.getProperty("prop1bis"));
        Assert.assertEquals("New property 1", profile.getProperty("prop1"));
        Assert.assertEquals("New property 2", profile.getProperty("prop2"));
        Assert.assertEquals("New property 3", profile.getProperty("prop3"));
    }
}
