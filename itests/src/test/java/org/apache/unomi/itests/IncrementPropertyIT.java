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
    public void testIncrementNotExistingPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView.acme");

        createRule(incrementPropertyAction);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());
            refreshPersistence();

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(1, value, 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView.acme");

        createRule(incrementPropertyAction);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);
        profile.setProperties(properties);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            refreshPersistence();

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(50, value, 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingPropertyWithExistingEventPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView.acme");
        incrementPropertyAction.setParameter("propertyTarget", "project.nasa");

        createRule(incrementPropertyAction);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);
        profile.setProperties(properties);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");
        properties = new HashMap<>();
        propertyValue = new HashMap<>();
        propertyValue.put("nasa", 19);
        properties.put("project", propertyValue);
        target.setProperties(properties);

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            refreshPersistence();

            int value = ((Map<String, Integer>) updatedProfile.getProperty("pageView")).get("acme");
            Assert.assertEquals(68, value, 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    @Test
    public void testIncrementNotExistingObjectPropertyWithExistingEventObjectPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView");
        incrementPropertyAction.setParameter("propertyTarget", "pageView");

        createRule(incrementPropertyAction);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        propertyValue.put("health", 18);
        propertyValue.put("sport", 99);
        properties.put("pageView", propertyValue);
        target.setProperties(properties);

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            refreshPersistence();

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(49, property.get("acme"), 0.0);
            Assert.assertEquals(18, property.get("health"), 0.0);
            Assert.assertEquals(99, property.get("sport"), 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingObjectPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView");

        createRule(incrementPropertyAction);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        propertyValue.put("nasa", 5);
        properties.put("pageView", propertyValue);
        profile.setProperties(properties);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            refreshPersistence();

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(50, property.get("acme"), 0.0);
            Assert.assertEquals(6, property.get("nasa"), 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    @Test
    public void testIncrementExistingObjectPropertyWithExistingEventObjectPropertyByAction() throws InterruptedException {
        Action incrementPropertyAction = new Action(definitionsService.getActionType("incrementPropertyAction"));
        incrementPropertyAction.setParameter("propertyName", "pageView");
        incrementPropertyAction.setParameter("propertyTarget", "pageView");

        createRule(incrementPropertyAction);

        Map<String, Object> properties = new HashMap<>();
        Map<String, Integer> propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        properties.put("pageView", propertyValue);
        profile.setProperties(properties);

        CustomItem target = new CustomItem("ITEM_ID_PAGE", ITEM_TYPE_PAGE);
        target.setScope("acme-space");
        properties = new HashMap<>();
        propertyValue = new HashMap<>();
        propertyValue.put("acme", 49);
        propertyValue.put("health", 88);
        propertyValue.put("sport", 9);
        properties.put("pageView", propertyValue);
        target.setProperties(properties);

        Event event = new Event("view", null, profile, null, null, target, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);
        refreshPersistence();

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            refreshPersistence();

            Map<String, Integer> property = ((Map<String, Integer>) updatedProfile.getProperty("pageView"));
            Assert.assertEquals(98, property.get("acme"), 0.0);
            Assert.assertEquals(88, property.get("health"), 0.0);
            Assert.assertEquals(9, property.get("sport"), 0.0);
        } else {
            throw new IllegalStateException("Profile was not updated");
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
