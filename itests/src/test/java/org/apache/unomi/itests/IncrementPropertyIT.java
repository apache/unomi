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

import java.util.*;

import javax.inject.Inject;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import static org.apache.unomi.itests.BasicIT.ITEM_TYPE_PAGE;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class IncrementPropertyIT extends BaseIT {
    private Profile profile;
    private Rule rule;
    private Event event;

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject
    @Filter(timeout = 600000)
    protected RulesService rulesService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Before
    public void setup() throws Exception {
        profile = createProfile();
        rule = new Rule();
    }

    @After
    public void tearDown() {
        rulesService.removeRule(rule.getItemId());
        profileService.delete(profile.getItemId(), false);
    }

    @Test
    public void testIncrementNotExistingPropertyWithDynamicName() throws InterruptedException {
        int eventCode = buildActionAndSendEvent("pageView.${eventProperty::target.scope}", null, null, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());
            refreshPersistence();

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme-space");
            Assert.assertEquals(1, value, 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyWithDynamicName() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme-space", 24);
        properties.put("pageView", propertyValue);

        int eventCode = buildActionAndSendEvent("pageView.${eventProperty::target.scope}", null, properties, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme-space");
            Assert.assertEquals(25, value, 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementNotExistingProperty() throws InterruptedException {
        int eventCode = buildActionAndSendEvent("pageView.acme", null, null, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(1, value, 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);

        int eventCode = buildActionAndSendEvent("pageView.acme", null, properties, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(50, value, 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyWithExistingEventProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);

        Map<String, Object> targetProperties = new HashMap<>();
        propertyValue = new HashMap<>();
        propertyValue.put("nasa", 19);
        targetProperties.put("project", propertyValue);

        int eventCode = buildActionAndSendEvent("pageView.acme", "project.nasa", properties, targetProperties);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(68, value, 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementNotExistingObjectPropertyWithExistingEventObjectProperty() throws InterruptedException {
        Map<String, Object> targetProperties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        propertyValue.put("health", 18);
        propertyValue.put("sport", 99);
        targetProperties.put("pageView", propertyValue);

        int eventCode = buildActionAndSendEvent("pageView", "pageView", null, targetProperties);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(49, property.get("acme"), 0.0);
            Assert.assertEquals(18, property.get("health"), 0.0);
            Assert.assertEquals(99, property.get("sport"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingObjectProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        propertyValue.put("nasa", 5);
        properties.put("pageView", propertyValue);

        int eventCode = buildActionAndSendEvent("pageView", null, properties, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(50, property.get("acme"), 0.0);
            Assert.assertEquals(6, property.get("nasa"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingObjectPropertyWithExistingEventObjectProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);

        Map<String, Object> targetProperties = new HashMap<>();
        Map<String, Integer> propertyValue1 = new HashMap<>();
        propertyValue1.put("acme", 31);
        propertyValue1.put("health", 88);
        propertyValue1.put("sport", 9);
        targetProperties.put("pageView", propertyValue1);

        int eventCode = buildActionAndSendEvent("pageView", "pageView", properties, targetProperties);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(80, property.get("acme"), 0.0);
            Assert.assertEquals(88, property.get("health"), 0.0);
            Assert.assertEquals(9, property.get("sport"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyNested() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> properties1 = new HashMap<>();
        Map<String, Object> properties2 = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("city", 13);
        properties2.put("state", propertyValue);
        properties1.put("country", properties2);
        properties.put("continent", properties1);

        int eventCode = buildActionAndSendEvent("continent.country.state.city", null, properties, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = (Map<String, Integer>) ((Map<String, Object>) ((Map<String, Object>) updatedProfile.getProperty("continent")).get("country")).get("state");
            Assert.assertEquals(14, property.get("city"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementNotExistingPropertyNested() throws InterruptedException {
        int eventCode = buildActionAndSendEvent("continent.country.state.city", null, null, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = (Map<String, Integer>) ((Map<String, Object>) ((Map<String, Object>) updatedProfile.getProperty("continent")).get("country")).get("state");
            Assert.assertEquals(1, property.get("city"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyNestedWithExistingEventProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> properties1 = new HashMap<>();
        Map<String, Object> properties2 = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("city", 13);
        properties2.put("state", propertyValue);
        properties1.put("country", properties2);
        properties.put("continent", properties1);

        Map<String, Object> targetProperties = new HashMap<>();
        Map<String, Object> properties3 = new HashMap<>();
        Map<String, Object> propertyValue1 = new HashMap<>();
        propertyValue1.put("zone", 107);
        properties3.put("mars", propertyValue1);
        targetProperties.put("planet", properties3);

        int eventCode = buildActionAndSendEvent("continent.country.state.city", "planet.mars.zone", properties, targetProperties);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Integer> property = (Map<String, Integer>) ((Map<String, Object>) ((Map<String, Object>) updatedProfile.getProperty("continent")).get("country")).get("state");
            Assert.assertEquals(120, property.get("city"), 0.0);
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementObjectPropertyContainsStringValue() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> propertyValue = new HashMap<>();
        propertyValue.put("books", 59);
        propertyValue.put("chapters", 1001);
        propertyValue.put("featured", "The forty rules");
        properties.put("library", propertyValue);

        int eventCode = buildActionAndSendEvent("library", null, properties, null);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Object> property = ((Map<String, Object>) updatedProfile.getProperty("library"));
            Assert.assertEquals(60, (int) property.get("books"), 0.0);
            Assert.assertEquals(1002, (int) property.get("chapters"), 0.0);
            Assert.assertEquals("The forty rules", property.get("featured"));
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testIncrementObjectPropertyContainsStringValueWithExistingEventProperty() throws InterruptedException {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> propertyValue = new HashMap<>();
        propertyValue.put("books", 59);
        propertyValue.put("chapters", 1001);
        propertyValue.put("featured", "The forty rules");
        properties.put("library", propertyValue);

        Map<String, Object> targetProperties = new HashMap<>();
        Map<String, Object> properties1 = new HashMap<>();
        Map<String, Object> propertyValue1 = new HashMap<>();
        propertyValue1.put("books", 222);
        propertyValue1.put("chapters", 2048);
        propertyValue1.put("featured", "Bible");
        properties1.put("library", propertyValue1);
        targetProperties.put("main", properties1);

        int eventCode = buildActionAndSendEvent("library", "main.library", properties, targetProperties);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Object> property = ((Map<String, Object>) updatedProfile.getProperty("library"));
            Assert.assertEquals(281, (int) property.get("books"), 0.0);
            Assert.assertEquals(3049, (int) property.get("chapters"), 0.0);
            Assert.assertEquals("The forty rules", property.get("featured"));
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    private void createRule(Action incrementPropertyAction) throws InterruptedException {
        Condition condition = createCondition();
        Metadata metadata = createMetadata();

        List<Action> actions = new ArrayList<>();
        actions.add(incrementPropertyAction);

        rule.setCondition(condition);
        rule.setActions(actions);
        rule.setMetadata(metadata);
        rulesService.setRule(rule);
        refreshPersistence();
    }

    private int buildActionAndSendEvent(String propertyName, String propertyTargetName, Map<String, Object> properties, Map<String, Object> targetProperties) throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", propertyName);
        if (propertyTargetName != null) incrementPropertyAction.setParameter("propertyTarget", propertyTargetName);

        createRule(incrementPropertyAction);

        if (properties != null) profile.setProperties(properties);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");
        if (targetProperties != null) target.setProperties(targetProperties);

        event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        return eventCode;
    }

    private Metadata createMetadata() {
        String itemId = UUID.randomUUID().toString();
        Metadata metadata = new Metadata();
        metadata.setId(itemId);
        metadata.setName(itemId);
        metadata.setDescription(itemId);
        metadata.setEnabled(true);
        metadata.setScope("systemscope");
        return metadata;
    }

    private Condition createCondition() {
        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", "view");
        return condition;
    }

    private Profile createProfile() throws InterruptedException {
        Profile profile = new Profile(UUID.randomUUID().toString());

        profileService.save(profile);
        refreshPersistence();

        return profile;
    }
}
