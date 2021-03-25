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
import org.junit.After;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 12/10/2017.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class CopyPropertiesActionIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(CopyPropertiesActionIT.class);

    private final static String EMPTY_PROFILE = "empty-profile";
    private final static String PROFILE_WITH_PROPERTIES = "profile-with-properties";

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
        profile.setItemId(PROFILE_WITH_PROPERTIES);
        profile.setProperties(new HashMap<>());
        profile.setProperty("lastName", "Jose"); // property that have a propertyType registered in the system
        profile.setProperty("singleValue", "A single value");
        profile.setProperty("existingArray", Arrays.asList("element1","element2"));
        profileService.save(profile);
        LOGGER.info("Profile saved with ID [{}].", profile.getItemId());

        Profile profileTarget = new Profile();
        profileTarget.setItemId(EMPTY_PROFILE);
        profileService.save(profileTarget);
        LOGGER.info("Profile saved with ID [{}].", profileTarget.getItemId());

        refreshPersistence();
    }

    @After
    public void cleanUp() throws IOException, InterruptedException {
        profileService.delete(PROFILE_WITH_PROPERTIES, false);
        profileService.delete(EMPTY_PROFILE, false);
        refreshPersistence();
    }

    private void initializePropertyType() {
        Metadata metadata = new Metadata();
        metadata.setSystemTags(new HashSet<>(Arrays.asList("urlParameters")));
        metadata.setId("arrayParam");
        metadata.setName("Array parameter");

        PropertyType propertyType1 = new PropertyType();
        propertyType1.setItemId("arrayParam");
        propertyType1.setMetadata(metadata);
        propertyType1.setTarget("profiles");
        propertyType1.setValueTypeId("string");
        propertyType1.setMultivalued(true);

        Metadata metadata2 = new Metadata();
        metadata2.setSystemTags(new HashSet<>(Arrays.asList("urlParameters")));
        metadata2.setId("singleParam");
        metadata2.setName("Single parameters");

        PropertyType propertyType2 = new PropertyType();
        propertyType2.setItemId("singleParam");
        propertyType2.setMetadata(metadata2);
        propertyType2.setTarget("profiles");
        propertyType2.setValueTypeId("string");
        propertyType2.setMultivalued(false);

        profileService.setPropertyType(propertyType1);
        profileService.setPropertyType(propertyType2);
    }

    private void initializePropertyTypeWithDifferentSystemTag() {
        Metadata metadata = new Metadata();
        metadata.setSystemTags(new HashSet<>(Arrays.asList("shouldBeAbsent")));
        metadata.setId("arrayParam");
        metadata.setName("Array parameter");

        PropertyType propertyType1 = new PropertyType();
        propertyType1.setItemId("arrayParam");
        propertyType1.setMetadata(metadata);
        propertyType1.setTarget("profiles");
        propertyType1.setValueTypeId("string");
        propertyType1.setMultivalued(true);

        profileService.setPropertyType(propertyType1);
    }

    @Test
    public void testCopyProperties_copyMultipleValueWithoutExistingPropertyTypeAndWithoutExistingValue()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyPropertiesWithoutSystemTags.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(EMPTY_PROFILE);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("arrayParam", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("arrayParam")).contains("valueA"));
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("arrayParam")).contains("valueB"));
    }

    @Test
    public void testCopyProperties_tryCopyArrayOnExistingSingleValue()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyPropertiesWithoutSystemTags.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_WITH_PROPERTIES);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("singleValue", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes == 0);
        Assert.assertTrue(((String) event.getProfile().getProperty("singleValue")).equals("A single value"));
    }

    @Test
    public void testCopyProperties_replaceSingleValue()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyPropertiesWithoutSystemTags.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_WITH_PROPERTIES);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("singleValue", "New value");

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertTrue(((String) event.getProfile().getProperty("singleValue")).equals("New value"));
    }

    @Test
    public void testCopyProperties_copyArrayIntoExistingArray()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyPropertiesWithoutSystemTags.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        Profile profile = profileService.load(PROFILE_WITH_PROPERTIES);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("existingArray", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("existingArray")).contains("element1"));
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("existingArray")).contains("element2"));
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("existingArray")).contains("valueA"));
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("existingArray")).contains("valueB"));
    }

    @Test
    public void testCopyProperties_copyArrayWithPropertyType()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyPropertiesWithoutSystemTags.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        initializePropertyType();
        Profile profile = profileService.load(EMPTY_PROFILE);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("arrayParam", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("arrayParam")).contains("valueA"));
        Assert.assertTrue(((List<String>) event.getProfile().getProperty("arrayParam")).contains("valueB"));
    }

    @Test
    public void testCopyProperties_tryCopyArrayWithPropertyTypeIntoSingleValue()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        initializePropertyType();
        Profile profile = profileService.load(EMPTY_PROFILE);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("singleParam", Arrays.asList("valueA", "valueB"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes == 0);
    }

    @Test
    public void testCopyProperties_replaceSingleValueWithPropertyType()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        initializePropertyType();
        Profile profile = profileService.load(EMPTY_PROFILE);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("singleParam", "New value");

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);
        Assert.assertTrue(((String) event.getProfile().getProperty("singleParam")).equals("New value"));
    }

    @Test
    public void testCopyProperties_mandatorySystemTagsNotPresent()
            throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper()
                .readValue(new File("data/tmp/testCopyProperties.json").toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        Thread.sleep(2000);

        initializePropertyTypeWithDifferentSystemTag();
        Profile profile = profileService.load(EMPTY_PROFILE);
        Assert.assertNull(profile.getProperty("lastname"));

        Event event = new Event("copyProperties", null, profile, null, null, profile, new Date());
        event.setPersistent(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("arrayParam", Arrays.asList("New value"));

        event.setProperty("urlParameters", properties);

        int changes = eventService.send(event);

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes == 0);
    }
}
