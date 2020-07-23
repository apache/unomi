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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * An integration test for the profile service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileServiceIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(ProfileServiceIT.class);

    private final static String TEST_PROFILE_ID = "test-profile-id";
    private final static String TEST_PROFILE_ID_TWO = "test-profile-id-two";
    private final static String TEST_PROFILE_ID_THREE = "test-profile-id-three";

    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;

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
    public void testGetProfileWithScrolling() {
        Profile profileOne = new Profile();
        Profile profileTwo = new Profile();
        Profile profileThree = new Profile();

        profileOne.setItemId(TEST_PROFILE_ID);
        profileOne.setItemId(TEST_PROFILE_ID_TWO);
        profileOne.setItemId(TEST_PROFILE_ID_THREE);

        profileService.save(profileOne);
        profileService.save(profileTwo);
        profileService.save(profileThree);

        Thread.sleep(4000); // Make sure Elastic is updated

        Query query = new Query();

        Query searchQuery = new Query();
        searchQuery.setLimit(2);
        searchQuery.setScrollTimeValidity("10m");

        PartialList<Profile> profiles = search(query, Profile.class);

        assertEquals(2, profiles.getList().size());

        String scrollIdentifier = profiles.getScrollIdentifier();
        searchQuery.setScrollIdentifier(scrollIdentifier);

        profiles = search(query, Profile.class);
        assertEquals(1, profiles.getList().size());
    }

}
