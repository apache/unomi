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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
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
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Date;

/**
 * Created by amidani on 12/10/2017.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class CopyPropertiesActionIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(CopyPropertiesActionIT.class);

    private final static String PROFILE_TARGET_TEST_ID = "profile-target-event";
    private final static String PROFILE_TEST_ID = "profile-to-update-by-event";

    @Inject
    @Filter(timeout = 600000)
    protected RulesService rulesService;
    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;
    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Before
    public void setUp() throws IOException, InterruptedException {
        Profile profile = new Profile();
        profile.setItemId(PROFILE_TEST_ID);
        profile.setProperties(new HashMap<>());
        profile.setProperty("lastName", "Jose"); // property that have a propertyType registered in the system
        profile.setProperty("prop4", "New property 4"); // property that do not have a propertyType registered in the system
        profileService.save(profile);
        LOGGER.info("Profile saved with ID [{}].", profile.getItemId());

        Profile profileTarget = new Profile();
        profileTarget.setItemId(PROFILE_TARGET_TEST_ID);
        profileService.save(profileTarget);
        LOGGER.info("Profile saved with ID [{}].", profileTarget.getItemId());

        refreshPersistence();
    }

    @Test
    public void testCopyProperties_copyMultipleValueWithExistingPropertyType() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("param1", Arrays.asList("value"));
        properties.put("param2", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        Metadata metadata = new Metadata();
        metadata.setSystemTags(new HashSet<>(Arrays.asList("urlParameters")));
        metadata.setId("param1");
        metadata.setName("Url parameters");

        PropertyType propertyType1 = new PropertyType();
        propertyType1.setItemId("param1");
        propertyType1.setMetadata(metadata);
        propertyType1.setTarget("profiles");
        propertyType1.setValueTypeId("string");
        propertyType1.setMultivalued(true);

        profileService.setPropertyType(propertyType1);

        Metadata metadata2 = new Metadata();
        metadata2.setSystemTags(new HashSet<>(Arrays.asList("urlParameters")));
        metadata2.setId("param2");
        metadata2.setName("Url parameters");

        PropertyType propertyType2 = new PropertyType();
        propertyType2.setItemId("param2");
        propertyType2.setMetadata(metadata2);
        propertyType2.setTarget("profiles");
        propertyType2.setValueTypeId("string");
        propertyType2.setMultivalued(true);

        profileService.setPropertyType(propertyType2);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }

    @Test
    public void testCopyProperties_copyMultipleValueWithoutExistingPropertyType() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("param1WithoutPropertyType", Arrays.asList("value"));
        properties.put("param2WithoutPropertyType", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }

    @Test
    public void testCopyProperties_copySingleValueWithoutPropertyType() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("param1SingleValue", "SingleValue");

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }

    @Test
    public void testCopyProperties_copySingleValueWithExistingPropertyType() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("param1SingleWithPropertyType", "SingleValue");

        event.setProperty("urlParameters", properties);

        Metadata metadata = new Metadata();
        metadata.setSystemTags(new HashSet<>(Arrays.asList("urlParameters")));
        metadata.setId("param1SingleWithPropertyType");
        metadata.setName("Url parameters");

        PropertyType propertyType1 = new PropertyType();
        propertyType1.setItemId("param1SingleWithPropertyType");
        propertyType1.setMetadata(metadata);
        propertyType1.setTarget("profiles");
        propertyType1.setValueTypeId("string");
        propertyType1.setMultivalued(false);

        profileService.setPropertyType(propertyType1);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }


    @Test
    public void testCopyProperties_copySingleValueWithExistingPropertyOnProfile() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        profile.setProperty("param1Existing", "existing");
        profile.setProperty("param2Existing", Arrays.asList("existingArray"));
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("param1Existing", "SingleValue");

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }

    @Test
    public void testCopyProperties_copySingleValueWithExistingArray() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        profile.setProperty("paramExistingArray", Arrays.asList("existingArray"));
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("paramExistingArray", "SingleValue");

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
    }

    @Test
    public void testCopyProperties_copyArrayOnSingleValueShouldNotCopy() throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_TARGET_TEST_ID);
        profile.setProperty("paramToNotReplace", "existingSingleValue");
        Assert.assertNull(profile.getProperty("firstName"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("paramToNotReplace", Arrays.asList("newArray"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes == 0);
    }
}
