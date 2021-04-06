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

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.services.TopicService;
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
public class IncrementInterestsIT extends BaseIT {
    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject
    @Filter(timeout = 600000)
    protected TopicService topicService;

    @Inject
    @Filter(timeout = 600000)
    protected RulesService rulesService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    private Profile profile;
    private Rule rule;
    private Topic topic;

    @Before
    public void setup() throws Exception {
        topic = createTopic("topicId");
        profile = createProfile();
        rule = new Rule();
    }

    @After
    public void tearDown() {
        rulesService.removeRule(rule.getItemId());
        topicService.delete(topic.getItemId());
        profileService.delete(profile.getItemId(), false);
    }

    @Test
    public void test() throws InterruptedException {
        Map<String, Double> interestsAsMap = new HashMap<>();
        interestsAsMap.put(topic.getTopicId(), 50.0);
        interestsAsMap.put("unknown", 10.0);

        Event event = createEvent(profile, interestsAsMap);

        int eventCode = eventService.send(event);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Double> interests = (Map<String, Double>) updatedProfile.getProperty("interests");

            Assert.assertEquals(0.5, interests.get(topic.getTopicId()), 0.0);
            Assert.assertFalse(interests.containsKey("unknown"));
        } else {
            Assert.fail("Profile was not updated");
        }
    }

    @Test
    public void testAction() throws InterruptedException {
        Action incrementAction = new Action(definitionsService.getActionType("incrementInterestAction"));
        incrementAction.setParameter("eventInterestProperty", "eventProperty::target.properties.interests");

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", "view");

        String itemId = UUID.randomUUID().toString();

        Metadata metadata = new Metadata();
        metadata.setId(itemId);
        metadata.setName(itemId);
        metadata.setDescription(itemId);
        metadata.setEnabled(true);
        metadata.setScope("systemscope");

        rule.setCondition(condition);
        List<Action> actions = new ArrayList<>();
        actions.add(incrementAction);
        rule.setActions(actions);
        rule.setMetadata(metadata);

        rulesService.setRule(rule);
        refreshPersistence();

        Map<String, Double> interestsAsMap = new HashMap<>();
        interestsAsMap.put(topic.getTopicId(), 50.0);
        interestsAsMap.put("unknown", 10.0);

        Map<String, Object> properties = new HashMap<>();

        properties.put("interests", interestsAsMap);

        CustomItem item = new CustomItem("page", ITEM_TYPE_PAGE);
        item.setProperties(properties);

        Event event = new Event("view", null, profile, null, null, item, new Date());
        event.setPersistent(false);

        int eventCode = eventService.send(event);

        if (eventCode == EventService.PROFILE_UPDATED) {
            Profile updatedProfile = profileService.save(event.getProfile());

            Map<String, Double> interests = (Map<String, Double>) updatedProfile.getProperty("interests");

            Assert.assertEquals(0.5, interests.get(topic.getTopicId()), 0.0);
            Assert.assertFalse(interests.containsKey("unknown"));
        } else {
            throw new IllegalStateException("Profile was not updated");
        }
    }

    private Event createEvent(Profile profile, Map<String, Double> interestsAsMap) {
        Event event = new Event("incrementInterest", null, profile, null, null, profile, new Date());

        event.setPersistent(false);
        event.setProperty("interests", interestsAsMap);

        return event;
    }

    private Topic createTopic(final String topicId) throws InterruptedException {
        Topic topic = new Topic();

        topic.setTopicId(topicId);
        topic.setItemId(topicId);
        topic.setName("topicName");
        topic.setScope("scope");

        topicService.save(topic);
        refreshPersistence();

        return topic;
    }

    private Profile createProfile() throws InterruptedException {
        Profile profile = new Profile(UUID.randomUUID().toString());

        profile.setProperty("firstName", "FirstName");
        profile.setProperty("lastName", "LastName");

        profileService.save(profile);
        refreshPersistence();

        return profile;
    }
}
