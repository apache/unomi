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

import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * An integration test for the profile service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileServiceIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileServiceIT.class);

    private final static String TEST_PROFILE_ID = "test-profile-id";

    private static final String TEST_PROFILE_ALIAS = "test-profile-alias";

    @Before
    public void setUp() {
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Profile.class, ProfileAlias.class, Event.class, Session.class);
    }

    @Test
    public void testProfileDelete() throws Exception {
        Profile profile = new Profile();
        profile.setItemId(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile not found in the required time", () -> profileService.load(TEST_PROFILE_ID), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        profileService.addAliasToProfile(profile.getItemId(), TEST_PROFILE_ALIAS, "defaultClientId");

        keepTrying("Profile alias not found in the required time", () -> profileService.load(TEST_PROFILE_ALIAS), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        LOGGER.info("Profile saved, now testing profile delete...");
        profileService.delete(TEST_PROFILE_ID, false);

        waitForNullValue("Profile still present after deletion", () -> profileService.load(TEST_PROFILE_ALIAS), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        LOGGER.info("Profile deleted successfully.");
    }

    @Test
    public void testProfileWithoutItemId() throws Exception {
        Profile profile = new Profile();
        profile.setProperty("name", "testProfileWithoutItemId");
        profileService.saveOrMerge(profile);

        Profile testProfile = keepTrying("Profile not found in the required time", () -> profileService.findProfilesByPropertyValue("properties.name", "testProfileWithoutItemId", 0, 10, null), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES).get(0);
        LOGGER.info("Ensure an itemId as been set...");
        Assert.assertNotNull(testProfile.getItemId());

        LOGGER.info("Delete profile...");
        profileService.delete(testProfile.getItemId(), false);

        waitForNullValue("Profile still present after deletion", () -> profileService.load(testProfile.getItemId()), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

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

        keepTrying("Profile " + profileIdOne + " not found in the required time", () -> profileService.load(profileIdOne), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile " + profileIdTwo + " not found in the required time", () -> profileService.load(profileIdTwo), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile " + profileIdThree + " not found in the required time", () -> profileService.load(profileIdThree),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

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
    public void testGetProfileWithWrongScrollerIdThrowException() throws InterruptedException, IOException {
        boolean throwExceptionCurrent = false;
        Configuration searchEngineConfiguration = configurationAdmin.getConfiguration("org.apache.unomi.persistence." + searchEngine);
        if (searchEngineConfiguration != null && searchEngineConfiguration.getProperties().get("throwExceptions") != null) {
            try {
                if (searchEngineConfiguration.getProperties().get("throwExceptions") instanceof String) {
                    throwExceptionCurrent = Boolean.parseBoolean((String) searchEngineConfiguration.getProperties().get("throwExceptions"));
                } else {
                    // already a boolean
                    throwExceptionCurrent = (Boolean) searchEngineConfiguration.getProperties().get("throwExceptions");
                }
            } catch (Throwable e) {
                // Not able to cast the property
            }
        }

        updateConfiguration(null, "org.apache.unomi.persistence." + searchEngine, "throwExceptions", true);

        Query query = new Query();
        query.setLimit(2);
        query.setScrollTimeValidity("10m");
        query.setScrollIdentifier("dummyScrollId");

        try {
            profileService.search(query, Profile.class);
            fail("search method didn't throw when expected");
        } catch (RuntimeException ex) {
            // Should get here since this scenario should throw exception
        } finally {
            updateConfiguration(null, "org.apache.unomi.persistence." + searchEngine, "throwExceptions",
                    throwExceptionCurrent);
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

            keepTrying("Profile " + profileID + " not found in the required time", () -> profileService.load(profileID), Objects::nonNull,
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            IntStream.range(1, 3).forEach(index -> {
                final String profileAlias = profileID + "_alias_" + index;
                profileService.addAliasToProfile(profileID, profileAlias, "clientID" + index);
            });

            Profile storedProfile = keepTrying("Profile " + profileID + " not found in the required time",
                    () -> profileService.load(profileID), Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

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
            waitForNullValue("Profile still present after deletion", () -> profileService.load(profileID), DEFAULT_TRYING_TIMEOUT,
                    DEFAULT_TRYING_TRIES);
            waitForNullValue("Profile still present after deletion", () -> profileService.load(profileID + "_alias_1"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            waitForNullValue("Profile still present after deletion", () -> profileService.load(profileID + "_alias_2"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
    }

    @Test
    public void testAliasCannotBeCreatedOnSameProfile() throws Exception {
        String profileID = UUID.randomUUID().toString();
        Profile profile = new Profile();
        profile.setItemId(profileID);
        profileService.save(profile);

        keepTrying("Profile " + profileID + " not found in the required time", () -> profileService.load(profileID), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        try {
            profileService.addAliasToProfile(profileID, profileID, "defaultClientId");
            fail("It should not be possible to create an Alias on the same profile ID");
        } catch (Exception e) {
            // do nothing, it's expected
        }
    }

    @Test
    public void testAliasCannotBeCreatedInCaseAlreadyExists() throws Exception {
        String profileID = UUID.randomUUID().toString();
        String alias = UUID.randomUUID().toString();
        Profile profile = new Profile();
        profile.setItemId(profileID);
        profileService.save(profile);

        keepTrying("Profile " + profileID + " not found in the required time", () -> profileService.load(profileID), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        profileService.addAliasToProfile(profileID, alias, "defaultClientId");

        keepTrying("Profile " + profileID + " not found in the required time", () -> profileService.load(alias), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        String otherProfileId = UUID.randomUUID().toString();
        try {
            profileService.addAliasToProfile(otherProfileId, alias, "defaultClientId");
            fail("It should not be possible to create an Alias when an alias already exists with same ID");
        } catch (Exception e) {
            // do nothing, it's expected
        }
    }

    @Test
    public void testProfilePurge() throws Exception {
        Date currentDate = new Date();
        LocalDateTime minus10Days = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusDays(10);
        LocalDateTime minus30Days = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusDays(30);
        Date currentDateMinus10Days = Date.from(minus10Days.atZone(ZoneId.systemDefault()).toInstant());
        Date currentDateMinus30Days = Date.from(minus30Days.atZone(ZoneId.systemDefault()).toInstant());

        long originalProfilesCount  = persistenceService.getAllItemsCount(Profile.ITEM_TYPE);

        // create inactive profiles since 10 days
        for (int i = 0; i < 150; i++) {
            Profile profile = new Profile("inactive-profile-to-be-purge-" + i);
            profile.setProperty("lastVisit", currentDateMinus10Days);
            profile.setProperty("firstVisit", currentDateMinus10Days);
            persistenceService.save(profile);
        }

        // create active profiles created 30 days ago
        for (int i = 0; i < 150; i++) {
            Profile profile = new Profile("old-profile-to-be-purge-" + i);
            profile.setProperty("lastVisit", currentDate);
            profile.setProperty("firstVisit", currentDateMinus30Days);
            persistenceService.save(profile);
        }

        // create active and recent profile
        for (int i = 0; i < 150; i++) {
            Profile profile = new Profile("active-profile" + i);
            profile.setProperty("lastVisit", currentDate);
            profile.setProperty("firstVisit", currentDate);
            persistenceService.save(profile);
        }

        keepTrying("Failed waiting for all profiles to be available", () -> profileService.getAllProfilesCount(),
                (count) -> count == (450 + originalProfilesCount), 1000, 100);

        // Try purge with 0 params: should have no effects
        profileService.purgeProfiles(0, 0);
        keepTrying("We should still have 450 profiles", () -> profileService.getAllProfilesCount(),
                (count) -> count == (450 + originalProfilesCount), 1000, 100);

        // Try purge inactive profiles since 20 days, should have no effects there is no such profiles
        profileService.purgeProfiles(20, 0);
        keepTrying("We should still have 450 profiles", () -> profileService.getAllProfilesCount(),
                (count) -> count == (450 + originalProfilesCount), 1000, 100);

        // Try purge inactive profiles since 20 days and/or older than 40 days, should have no effects there is no such profiles
        profileService.purgeProfiles(20, 40);
        keepTrying("We should still have 450 profiles", () -> profileService.getAllProfilesCount(),
                (count) -> count == (450 + originalProfilesCount), 1000, 100);

        // Try purge inactive profiles since 5 days
        profileService.purgeProfiles(5, 0);
        keepTrying("Inactive profiles should be purge so we should have 300 profiles now", () -> profileService.getAllProfilesCount(),
                (count) -> count == (300 + originalProfilesCount), 1000, 100);

        // Try purge inactive profiles since 5 days and/or older than 25 days
        profileService.purgeProfiles(5, 25);
        keepTrying("Older profiles should be purge so we should have 150 profiles now", () -> profileService.getAllProfilesCount(),
                (count) -> count == (150 + originalProfilesCount), 1000, 100);
    }

    @Test
    public void testOldItemsPurge() throws Exception {
        Date currentDate = new Date();
        LocalDateTime minus10Months = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusMonths(10);
        LocalDateTime minus30Months = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusMonths(30);
        Date currentDateMinus10Months = Date.from(minus10Months.atZone(ZoneId.systemDefault()).toInstant());
        Date currentDateMinus30Months = Date.from(minus30Months.atZone(ZoneId.systemDefault()).toInstant());

        long originalSessionsCount  = persistenceService.getAllItemsCount(Session.ITEM_TYPE);
        long originalEventsCount  = persistenceService.getAllItemsCount(Event.ITEM_TYPE);

        Profile profile = new Profile("dummy-profile-old-items-purge-test");
        persistenceService.save(profile);

        // create 10 months old items
        for (int i = 0; i < 150; i++) {
            Session session = new Session("10months-old-session-" + i, profile, currentDateMinus10Months, "dummy-scope");
            persistenceService.save(session);
            persistenceService.save(new Event("10months-old-event-" + i, "view", session, profile, "dummy-scope", null, null, currentDateMinus10Months));
        }

        // create 30 months old items
        for (int i = 0; i < 150; i++) {
            Session session = new Session("30months-old-session-" + i, profile, currentDateMinus30Months, "dummy-scope");
            persistenceService.save(session);
            persistenceService.save(new Event("30months-old-event-" + i, "view", session, profile, "dummy-scope", null, null, currentDateMinus30Months));
        }

        // create 30 months old items
        for (int i = 0; i < 150; i++) {
            Session session = new Session("recent-session-" + i, profile, currentDate, "dummy-scope");
            persistenceService.save(session);
            persistenceService.save(new Event("recent-event-" + i, "view", session, profile, "dummy-scope", null, null, currentDate));
        }

        keepTrying("Sessions number should be 450", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (450 + originalSessionsCount), 1000, 100);
        keepTrying("Events number should be 450", () -> persistenceService.getAllItemsCount(Event.ITEM_TYPE),
                (count) -> count == (450 + originalEventsCount), 1000, 100);

        // Should have no effect
        profileService.purgeSessionItems(0);
        keepTrying("Sessions number should be 450", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (450 + originalSessionsCount), 1000, 100);
        profileService.purgeEventItems(0);
        keepTrying("Events number should be 450", () -> persistenceService.getAllItemsCount(Event.ITEM_TYPE),
                (count) -> count == (450 + originalEventsCount), 1000, 100);

        // Should have no effect there is no sessions items older than 1200 days
        profileService.purgeSessionItems(1200);
        keepTrying("Sessions number should be 450", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (450 + originalSessionsCount), 1000, 100);
        // Should have no effect there is no events items older than 1200 days
        profileService.purgeEventItems(1200);
        keepTrying("Events number should be 450", () -> persistenceService.getAllItemsCount(Event.ITEM_TYPE),
                (count) -> count == (450 + originalEventsCount), 1000, 100);

        // Should purge sessions older than 750 days
        profileService.purgeSessionItems(750);
        keepTrying("Sessions number should be 300", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (300 + originalSessionsCount), 1000, 100);
        // Should purge events older than 750 days
        profileService.purgeEventItems(750);
        keepTrying("Events number should be 300", () -> persistenceService.getAllItemsCount(Event.ITEM_TYPE),
                (count) -> count == (300 + originalEventsCount), 1000, 100);

        // Should purge sessions older than 150 days
        profileService.purgeSessionItems(150);
        keepTrying("Sessions number should be 150", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (150 + originalSessionsCount), 1000, 100);
        // Should purge events older than 150 days
        profileService.purgeEventItems(150);
        keepTrying("Events number should be 150", () -> persistenceService.getAllItemsCount(Event.ITEM_TYPE),
                (count) -> count == (150 + originalEventsCount), 1000, 100);
    }

    @Test
    public void testBatchProfileUpdate() throws Exception {
        // Create 50 profiles
        for (int i = 1; i <= 50; i++) {
            Profile profile = new Profile();
            profile.setItemId("batchProfileUpdateTest" + i);
            profile.setProperty("name", "Boby");
            profile.setProperty("test", "batchProfileUpdateTest");

            profileService.save(profile);
        }

        Condition batchUpdateCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        batchUpdateCondition.setParameter("propertyName","properties.test");
        batchUpdateCondition.setParameter("comparisonOperator","equals");
        batchUpdateCondition.setParameter("propertyValue", "batchProfileUpdateTest");
        keepTrying("We should wait for profiles to be saved", () -> persistenceService.queryCount(batchUpdateCondition, Profile.ITEM_TYPE),
                (count) -> count == 50, 1000, 100);

        BatchUpdate batchUpdate = new BatchUpdate();
        batchUpdate.setCondition(batchUpdateCondition);
        batchUpdate.setStrategy("alwaysSet");
        batchUpdate.setPropertyName("properties.name");
        batchUpdate.setPropertyValue("Billybob");
        batchUpdate.setScrollBatchSize(10);
        profileService.batchProfilesUpdate(batchUpdate);

        Condition updatedProfilesCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        updatedProfilesCondition.setParameter("propertyName","properties.name");
        updatedProfilesCondition.setParameter("comparisonOperator","equals");
        updatedProfilesCondition.setParameter("propertyValue", "Billybob");
        keepTrying("We should still retrieve the 50 updated profiles", () -> persistenceService.queryCount(updatedProfilesCondition, Profile.ITEM_TYPE),
                (count) -> count == 50, 1000, 100);

        Condition oldProfilesCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        oldProfilesCondition.setParameter("propertyName","properties.name");
        oldProfilesCondition.setParameter("comparisonOperator","equals");
        oldProfilesCondition.setParameter("propertyValue", "Boby");
        keepTrying("We should not be able to retrieve previous profile based on previous value", () -> persistenceService.queryCount(oldProfilesCondition, Profile.ITEM_TYPE),
                (count) -> count == 0, 1000, 100);
    }

    @Test
    public void testPurgeSessions() throws Exception {
        Date currentDate = new Date();
        LocalDateTime minus6Months = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusMonths(6);
        LocalDateTime minus18Months = LocalDateTime.ofInstant(currentDate.toInstant(), ZoneId.systemDefault()).minusMonths(18);
        Date currentDateMinus6Months = Date.from(minus6Months.atZone(ZoneId.systemDefault()).toInstant());
        Date currentDateMinus18Months = Date.from(minus18Months.atZone(ZoneId.systemDefault()).toInstant());

        long originalSessionsCount  = persistenceService.getAllItemsCount(Session.ITEM_TYPE);

        //Create 10 profiles with sessions
        Profile[] profiles = new Profile[10];
        for (int i=0; i < profiles.length; i++) {
            profiles[i] = new Profile("dummy-profile-session-purge-test-" + i);
            profiles[i].setProperty("nbOfVisits", 20);
            profiles[i].setProperty("totalNbOfVisits", 20);
            persistenceService.save(profiles[i]);
        }

        // create 6 months old sessions
        for (int i = 0; i < profiles.length * 10; i++) {
            Session session = new Session("6-months-old-session-" + i, profiles[i%10], currentDateMinus6Months, "dummy-scope");
            persistenceService.save(session);
        }

        // create 18 months old sessions
        for (int i = 0; i < profiles.length * 10; i++) {
            Session session = new Session("18-months-old-session-" + i, profiles[i%10], currentDateMinus18Months, "dummy-scope");
            persistenceService.save(session);
        }

        keepTrying("Sessions number should be 200", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (200 + originalSessionsCount), 1000, 100);
        for (Profile value : profiles) {
            String profileId = value.getItemId();
            keepTrying("Profile should have nbOfVisits=20", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("nbOfVisits") == 20, 1000, 100);
            keepTrying("Profile should have totalNbOfVisits=20", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("totalNbOfVisits") == 20, 1000, 100);
        }

        // Should have no effect
        profileService.purgeSessionItems(0);
        keepTrying("Sessions number should be 200", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (200 + originalSessionsCount), 1000, 100);
        for (Profile value : profiles) {
            String profileId = value.getItemId();
            keepTrying("Profile should have nbOfVisits=20", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("nbOfVisits") == 20, 1000, 100);
            keepTrying("Profile should have totalNbOfVisits=20", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("totalNbOfVisits") == 20, 1000, 100);
        }

        // Should purge sessions older than 365 days
        profileService.purgeSessionItems(365);
        keepTrying("Sessions number should be 100", () -> persistenceService.getAllItemsCount(Session.ITEM_TYPE),
                (count) -> count == (100 + originalSessionsCount), 1000, 100);
        for (Profile value : profiles) {
            String profileId = value.getItemId();
            keepTrying("Profile should have nbOfVisits=10", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("nbOfVisits") == 10, 1000, 100);
            keepTrying("Profile should have totalNbOfVisits=20", () -> profileService.load(profileId),
                    (profile) -> (Integer) profile.getProperty("totalNbOfVisits") == 20, 1000, 100);
        }

    }
}
