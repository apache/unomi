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
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.EventService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SendEventActionIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(SendEventActionIT.class);

    private final static String TEST_RULE_ID = "sendEventTest";
    private final static String EVENT_ID = "sendEventTestId";
    private final static String TEST_EVENT_TYPE = "sendEventTestEventType";
    private final static String TEST_PROFILE_ID = "sendEventTestProfileId";

    @After
    public void tearDown() throws InterruptedException {
        eventService.removeProfileEvents(TEST_PROFILE_ID);
        rulesService.removeRule(TEST_RULE_ID);
        waitForNullValue("Event has not been deleted", () -> eventService.getEvent(EVENT_ID), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        waitForNullValue("Rule " + TEST_RULE_ID + "has not been deleted", () -> rulesService.getRule(TEST_RULE_ID), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testSendEventNotPersisted() throws InterruptedException {
        createAndWaitForRule(createSendEventRule(false));

        Assert.assertEquals(TEST_PROFILE_ID, sendEvent().getProfile().getItemId());

        shouldBeTrueUntilEnd("Event should not have been persisted", () -> eventService.searchEvents(getSearchCondition(), 0, 1),
                (eventPartialList -> eventPartialList.size() == 0), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testSendEventPersisted() throws InterruptedException {
        createAndWaitForRule(createSendEventRule(true));

        Assert.assertEquals(TEST_PROFILE_ID, sendEvent().getProfile().getItemId());

        keepTrying("Event should have been persisted", () -> eventService.searchEvents(getSearchCondition(), 0, 1),
                (eventPartialList -> eventPartialList.size() == 1), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    private Event sendEvent() {
        Profile profile = new Profile();
        profile.setProperties(new HashMap<>());
        profile.setItemId(TEST_PROFILE_ID);
        profile.setProperty("j:nodename", "michel");
        Event testEvent = new Event(TEST_EVENT_TYPE, null, profile, null, null, profile, new Date());
        testEvent.setItemId(EVENT_ID);
        int result = eventService.send(testEvent);
        LOGGER.info("Event processing result: {}", result);
        if (result == EventService.ERROR) {
            LOGGER.error("Event processing resulted in ERROR. Event details: {}", testEvent);
        }
        return testEvent;
    }

    private Condition getSearchCondition() {
        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "eventType");
        condition.setParameter("propertyValue", "sentFromAction");
        condition.setParameter("comparisonOperator", "equals");

        return condition;
    }

    private Rule createSendEventRule(boolean toBePersisted) {
        Rule sendEventRule = new Rule();
        sendEventRule.setMetadata(new Metadata(null, TEST_RULE_ID, TEST_RULE_ID, "Test rule for testing SendEventAction"));

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", TEST_EVENT_TYPE);
        sendEventRule.setCondition(condition);

        final Action action = new Action(definitionsService.getActionType("sendEventAction"));
        action.setParameter("eventType", "sentFromAction");
        action.setParameter("eventTarget", profileService.load(TEST_PROFILE_ID));
        action.setParameter("eventProperties", new HashMap<String, Object>());
        action.setParameter("toBePersisted", toBePersisted);
        sendEventRule.setActions(Collections.singletonList(action));

        return sendEventRule;
    }
}
