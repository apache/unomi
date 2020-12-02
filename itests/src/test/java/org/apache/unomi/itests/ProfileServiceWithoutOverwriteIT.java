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

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * An integration test for the profile service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileServiceWithoutOverwriteIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileServiceWithoutOverwriteIT.class);

    private final static String TEST_PROFILE_ID = "test-profile-id";

    @Configuration
    public Option[] config() throws InterruptedException {
        List<Option> options = new ArrayList<>();
        options.addAll(Arrays.asList(super.config()));
        options.add(systemProperty("org.apache.unomi.elasticsearch.throwExceptions").value("true"));
        options.add(systemProperty("org.apache.unomi.elasticsearch.alwaysOverwrite").value("false"));
        return options.toArray(new Option[0]);
    }

    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Before
    public void setUp() {
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
    }

    private Profile setupWithoutOverwriteTests() {
        Profile profile = new Profile();
        profile.setItemId(TEST_PROFILE_ID);
        profile.setProperty("country", "test-country");
        profile.setProperty("state", "test-state");
        profileService.save(profile);

        return profile;
    }

    @Test(expected = RuntimeException.class)
    public void testSaveProfileWithoutOverwriteSameProfileThrowsException() {
        Profile profile = setupWithoutOverwriteTests();
        profile.setProperty("country", "test2-country");
        profileService.save(profile);
    }

    @Test
    public void testSaveProfileWithoutOverwriteSavesAfterReload() throws InterruptedException {
        Profile profile = setupWithoutOverwriteTests();
        String profileId = profile.getItemId();
        Thread.sleep(4000);

        Profile updatedProfile = profileService.load(profileId);
        updatedProfile.setProperty("country", "test2-country");
        profileService.save(updatedProfile);

        Thread.sleep(4000);

        Profile profileWithNewCountry = profileService.load(profileId);
        assertEquals(profileWithNewCountry.getProperty("country"), "test2-country");
    }

    @Test(expected = RuntimeException.class)
    public void testSaveProfileWithoutOverwriteWrongSeqNoThrowsException() throws InterruptedException {
        Profile profile = setupWithoutOverwriteTests();
        String profileId = profile.getItemId();

        Thread.sleep(4000);

        Profile updatedProfile = profileService.load(profileId);
        updatedProfile.setProperty("country", "test2-country");
        updatedProfile.setSystemMetadata("seq_no", 1L);
        profileService.save(updatedProfile);
    }
}
