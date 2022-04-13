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
import org.apache.unomi.api.ProfileAlias;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.persistence.elasticsearch.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;

import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.inject.Inject;

/**
 * An integration test for the profile service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileServiceIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileServiceIT.class);

    private final static String TEST_PROFILE_ID = "test-profile-id";

    private static final String TEST_PROFILE_ALIAS = "test-profile-alias";

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

    @Test
    public void testProfileDelete() throws Exception {
        Profile profile = new Profile();
        profile.setItemId(TEST_PROFILE_ID);
        profileService.save(profile);

        refreshPersistence();

        profileService.addAliasToProfile(profile.getItemId(), TEST_PROFILE_ALIAS, "defaultClientId");

        refreshPersistence();

        LOGGER.info("Profile saved, now testing profile delete...");
        profileService.delete(TEST_PROFILE_ID, false);

        refreshPersistence();

        assertNull(profileService.load(TEST_PROFILE_ALIAS));

        LOGGER.info("Profile deleted successfully.");
    }

    @Test
    public void testGetProfileWithScrolling() throws InterruptedException {
        final String profileIdOne = "test-profile-id-one";
        final String profileIdTwo = "test-profile-id-two";
        final String profileIdThree = "test-profile-id-three";

        Profile profileOne = new Profile();
        Profile profileTwo = new Profile();
        Profile profileThree = new Profile();

        profileOne.setItemId(profileIdOne);
        profileTwo.setItemId(profileIdTwo);
        profileThree.setItemId(profileIdThree);

        profileService.save(profileOne);
        profileService.save(profileTwo);
        profileService.save(profileThree);

        Thread.sleep(4000); // Make sure Elastic is updated

        Query query = new Query();
        query.setLimit(2);
        query.setScrollTimeValidity("10m");

        PartialList<Profile> profiles = profileService.search(query, Profile.class);
        assertEquals(2, profiles.getList().size());

        Query queryCont = new Query();
        queryCont.setScrollTimeValidity("10m");
        queryCont.setScrollIdentifier(profiles.getScrollIdentifier());

        profiles = profileService.search(queryCont, Profile.class);
        assertEquals(1, profiles.getList().size());

        queryCont.setScrollIdentifier(profiles.getScrollIdentifier());
        profiles = profileService.search(queryCont, Profile.class);
        assertEquals(0, profiles.getList().size());
    }

    // Relevant only when throwExceptions system property is true
    @Test
    public void testGetProfileWithWrongScrollerIdThrowException() throws InterruptedException, NoSuchFieldException, IllegalAccessException, IOException {
        boolean throwExceptionCurrent = false;
        Configuration elasticSearchConfiguration = configurationAdmin.getConfiguration("org.apache.unomi.persistence.elasticsearch");
        if (elasticSearchConfiguration != null) {
            throwExceptionCurrent = Boolean.getBoolean((String) elasticSearchConfiguration.getProperties().get("throwExceptions"));
        }

        updateConfiguration(PersistenceService.class.getName(), "org.apache.unomi.persistence.elasticsearch", "throwExceptions", true);

        Query query = new Query();
        query.setLimit(2);
        query.setScrollTimeValidity("10m");
        query.setScrollIdentifier("dummyScrollId");

        try {
            profileService.search(query, Profile.class);
            fail("search method didn't throw when expected");
        } catch (RuntimeException ex) {
            // Should get here since this scenario should throw exception
        }
        finally {
            updateConfiguration(PersistenceService.class.getName(), "org.apache.unomi.persistence.elasticsearch", "throwExceptions", throwExceptionCurrent);
        }
    }

    @Test
    public void test_EventGetNestedProperty() {
        String nestedProperty = "outerProperty.innerProperty";
        String testValue = "test-value";
        String profileId = "test-profile-id";
        Profile profile = new Profile(profileId);
        final Map<String, String> innerProperty = new HashMap<>();
        innerProperty.put("innerProperty", testValue);
        profile.setProperty("outerProperty", innerProperty);
        String value = (String) profile.getNestedProperty(nestedProperty);
        assertEquals(testValue, value);
    }

    @Test
    public void testLoadProfileByAlias() throws Exception {
        String profileID = UUID.randomUUID().toString();

        try {
            Profile profile = new Profile();
            profile.setItemId(profileID);
            profileService.save(profile);

            refreshPersistence();

            IntStream.range(1, 3).forEach(index -> {
                final String profileAlias = profileID + "_alias_" + index;
                profileService.addAliasToProfile(profileID, profileAlias, "clientID" + index);
            });

            refreshPersistence();

            Profile storedProfile = profileService.load(profileID);
            assertNotNull(storedProfile);
            assertEquals(profileID, storedProfile.getItemId());

            storedProfile = profileService.load(profileID + "_alias_1");
            assertNotNull(storedProfile);
            assertEquals(profileID, storedProfile.getItemId());

            storedProfile = profileService.load(profileID + "_alias_2");
            assertNotNull(storedProfile);
            assertEquals(profileID, storedProfile.getItemId());

            PartialList<ProfileAlias> aliasList = profileService.findProfileAliases(profileID, 0, 10, null);
            assertEquals(2, aliasList.size());
        } finally {
            IntStream.range(1, 3).forEach(index -> {
                final String profileAlias = profileID + "_alias_" + index;
                profileService.removeAliasFromProfile(profileID, profileAlias, "clientID" + index);
            });

            profileService.delete(profileID, false);
        }
    }

}
