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
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RulesService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

/**
 * Integration test for MergeProfilesOnPropertyAction
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileMergeIT extends BaseIT {

    @Inject @Filter(timeout = 600000)
    protected EventService eventService;
    @Inject @Filter(timeout = 600000)
    protected RulesService rulesService;
    @Inject @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    private final static String TEST_EVENT_TYPE = "mergeProfileTestEventType";
    private final static String TEST_RULE_ID = "mergeOnPropertyTest";
    private final static String TEST_PROFILE_ID = "mergeOnPropertyTestProfileId";

    @After
    public void after() {
        // cleanup created data
        rulesService.removeRule(TEST_RULE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_dont_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(false));

        // A new profile should be created.
        Assert.assertNotEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(true));

        // No new profile should be created, instead the profile of the event should be used.
        Assert.assertEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    private Event sendEvent() {
        Profile profile = new Profile();
        profile.setProperties(new HashMap<>());
        profile.setItemId(TEST_PROFILE_ID);
        profile.setProperty("j:nodename", "michel");
        profile.getSystemProperties().put("mergeIdentifier", "jose");
        Event testEvent = new Event(TEST_EVENT_TYPE, null, profile, null, null, profile, new Date());
        eventService.send(testEvent);
        return testEvent;
    }

    private Rule createMergeOnPropertyRule(boolean forceEventProfileAsMaster) throws InterruptedException {
        Rule mergeOnPropertyTestRule = new Rule();
        mergeOnPropertyTestRule.setMetadata(new Metadata(null, TEST_RULE_ID, TEST_RULE_ID, "Test rule for testing MergeProfilesOnPropertyAction"));

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", TEST_EVENT_TYPE);
        mergeOnPropertyTestRule.setCondition(condition);

        final Action mergeProfilesOnPropertyAction = new Action( definitionsService.getActionType( "mergeProfilesOnPropertyAction"));
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyValue", "eventProperty::target.properties(j:nodename)");
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyName", "mergeIdentifier");
        mergeProfilesOnPropertyAction.setParameter("forceEventProfileAsMaster", forceEventProfileAsMaster);
        mergeOnPropertyTestRule.setActions(Collections.singletonList(mergeProfilesOnPropertyAction));

        return mergeOnPropertyTestRule;
    }
}
