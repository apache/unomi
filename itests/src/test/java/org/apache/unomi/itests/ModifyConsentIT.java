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

import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
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
import java.util.Date;
import java.util.Objects;

/**
 * An integration test for consent modifications using Apache Unomi @Event
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ModifyConsentIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ModifyConsentIT.class);

    private final static String PROFILE_TEST_ID = "profile-consent";

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Before
    public void setUp() throws InterruptedException {
        Profile profile = new Profile();
        profile.setItemId(PROFILE_TEST_ID);
        profileService.save(profile);
        keepTrying("Profile " + PROFILE_TEST_ID + " not found in the required time", () -> profileService.load(PROFILE_TEST_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        LOGGER.info("Profile saved with ID [{}].", profile.getItemId());
    }

    @Test
    public void testConsentGrant() throws InterruptedException {
        Profile profile = profileService.load(PROFILE_TEST_ID);
        Assert.assertNotNull(profile);
        Assert.assertEquals(0, profile.getConsents().size());

        Event modifyConsentEvent = new Event("modifyConsent", null, profile, null, null, null, new Date());
        modifyConsentEvent.setPersistent(false);

        ISO8601DateFormat dateFormat = new ISO8601DateFormat();
        Consent consent1 = new Consent("scope", "consentType01", ConsentStatus.GRANTED, new Date(), null);
        modifyConsentEvent.setProperty("consent", consent1.toMap(dateFormat));
        int changes = eventService.send(modifyConsentEvent);
        Consent consent2 = new Consent("scope", "consentType02", ConsentStatus.GRANTED, new Date(), null);
        modifyConsentEvent.setProperty("consent", consent2.toMap(dateFormat));
        changes |= eventService.send(modifyConsentEvent);

        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
        }

        LOGGER.info("Changes of the event : {}", changes);

        Assert.assertTrue(changes > 0);

        keepTrying("Profile " + PROFILE_TEST_ID + " not found in the required time", () -> profileService.load(PROFILE_TEST_ID),
                loadedProfile -> loadedProfile.getConsents().size() == 2, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }
}
