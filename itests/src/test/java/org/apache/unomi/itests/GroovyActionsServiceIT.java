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

import groovy.lang.GroovyCodeSource;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
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

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class GroovyActionsServiceIT extends BaseIT {

    public static final String UPDATE_ADDRESS_GROOVY_ACTION = "updateAddressGroovyAction";
    public static final String PROFILE_ID = "profile1";
    public static final String UPDATE_ADDRESS_ACTION_GROOVY_FILE = "data/tmp/groovy/UpdateAddressAction.groovy";
    public static final String UPDATE_ADDRESS_ACTION = "UpdateAddressAction";

    @Inject
    @Filter(timeout = 600000)
    protected GroovyActionsService groovyActionsService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

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
    public void setUp() throws InterruptedException {
        Profile profile = new Profile();
        profile.setItemId(PROFILE_ID);
        profile.setProperties(new HashMap<>());
        profile.setProperty("lastName", "Jose");
        profile.setProperty("firstname", "Alexandre");
        profile.setProperty("address", "Address");
        profileService.save(profile);
        refreshPersistence();
    }

    @After
    public void cleanUp() throws InterruptedException {
        profileService.delete(PROFILE_ID, false);
        refreshPersistence();
    }

    private String loadGroovyAction(String pathname) throws IOException {
        return IOUtils.toString(new FileInputStream(new File(pathname)));
    }

    private void createRule(String filename) throws IOException, InterruptedException {
        Rule rule = CustomObjectMapper.getObjectMapper().readValue(new File(filename).toURI().toURL(), Rule.class);
        rulesService.setRule(rule);
        refreshPersistence();
    }

    private Event sendGroovyActionEvent() {
        Profile profile = profileService.load(PROFILE_ID);

        Event event = new Event("updateAddress", null, profile, null, null, profile, new Date());

        event.setProperty("address", "New address");

        eventService.send(event);
        return event;
    }

    @Test
    public void testGroovyActionsService_triggerGroovyAction() throws IOException, InterruptedException {
        createRule("data/tmp/testRuleGroovyAction.json");
        groovyActionsService.save(UPDATE_ADDRESS_ACTION, loadGroovyAction(UPDATE_ADDRESS_ACTION_GROOVY_FILE));

        keepTrying("Failed waiting for the creation of the GroovyAction for the trigger action test",
                () -> groovyActionsService.getGroovyCodeSource(UPDATE_ADDRESS_ACTION), Objects::nonNull, 1000, 100);

        ActionType actionType = keepTrying("Failed waiting for the creation of the GroovyAction for trigger action test",
                () -> definitionsService.getActionType(UPDATE_ADDRESS_GROOVY_ACTION), Objects::nonNull, 1000, 100);

        Assert.assertNotNull(actionType);
        // Refresh rules to enable rule
        rulesService.refreshRules();

        Event event = sendGroovyActionEvent();

        Assert.assertEquals("New address", event.getProfile().getProperty("address"));
    }

    @Test
    public void testGroovyActionsService_saveActionAndTestSavedValues() throws IOException, InterruptedException, ClassNotFoundException {
        groovyActionsService.save(UPDATE_ADDRESS_ACTION, loadGroovyAction(UPDATE_ADDRESS_ACTION_GROOVY_FILE));

        ActionType actionType = keepTrying("Failed waiting for the creation of the GroovyAction for the save test",
                () -> definitionsService.getActionType(UPDATE_ADDRESS_GROOVY_ACTION), Objects::nonNull, 1000, 100);

        Assert.assertEquals(UPDATE_ADDRESS_ACTION, groovyActionsService.getGroovyCodeSource(UPDATE_ADDRESS_ACTION).getName());

        Assert.assertTrue(actionType.getMetadata().getId().contains(UPDATE_ADDRESS_GROOVY_ACTION));
        Assert.assertEquals(2, actionType.getMetadata().getSystemTags().size());
        Assert.assertTrue(actionType.getMetadata().getSystemTags().contains("tag1"));
        Assert.assertEquals(2, actionType.getParameters().size());
        Assert.assertEquals("param1", actionType.getParameters().get(0).getId());

        Assert.assertEquals("groovy:UpdateAddressAction", actionType.getActionExecutor());
        Assert.assertFalse(actionType.getMetadata().isHidden());
    }

    @Test
    public void testGroovyActionsService_removeGroovyAction() throws IOException, InterruptedException {
        groovyActionsService.save(UPDATE_ADDRESS_ACTION, loadGroovyAction(UPDATE_ADDRESS_ACTION_GROOVY_FILE));

        GroovyCodeSource groovyCodeSource = keepTrying("Failed waiting for the creation of the GroovyAction for the remove test",
                () -> groovyActionsService.getGroovyCodeSource(UPDATE_ADDRESS_ACTION), Objects::nonNull, 1000, 100);

        Assert.assertNotNull(groovyCodeSource);

        groovyActionsService.remove(UPDATE_ADDRESS_ACTION);
        refreshPersistence();

        Thread.sleep(2000);
        groovyCodeSource = groovyActionsService.getGroovyCodeSource(UPDATE_ADDRESS_ACTION);

        Assert.assertNull(groovyCodeSource);

        ActionType actionType = definitionsService.getActionType(UPDATE_ADDRESS_GROOVY_ACTION);

        Assert.assertNull(actionType);

    }
}
