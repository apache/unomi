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
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.persistence.elasticsearch.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;

import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * An integration test for the profile service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileServiceIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileServiceIT.class);

    private final static String TEST_PROFILE_ID = "test-profile-id";

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
    public void testProfileDelete() {
        Profile profile = new Profile();
        profile.setItemId(TEST_PROFILE_ID);
        profileService.save(profile);
        LOGGER.info("Profile saved, now testing profile delete...");
        profileService.delete(TEST_PROFILE_ID, false);
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
    public void testGetProfileWithWrongScrollerIdThrowException() throws InterruptedException {
        ElasticSearchPersistenceServiceImpl esPersistenceService = (ElasticSearchPersistenceServiceImpl)persistenceService;
        esPersistenceService.setThrowExceptions(true);

        Query query = new Query();
        query.setLimit(2);
        query.setScrollTimeValidity("10m");
        query.setScrollIdentifier("dummyScrollId");

        try {
            profileService.search(query, Profile.class);
            fail("search method didn't throw when expected");
        } catch (RuntimeException ex) {
        }
        finally {
            esPersistenceService.setThrowExceptions(false);
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


}
