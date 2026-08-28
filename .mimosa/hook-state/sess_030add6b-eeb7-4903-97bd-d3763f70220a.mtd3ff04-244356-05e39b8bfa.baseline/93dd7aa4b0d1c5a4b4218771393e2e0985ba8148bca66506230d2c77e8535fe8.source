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
 * limitations under the License.
 */
package org.apache.unomi.itests.graphql;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.unomi.api.Profile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

public class GraphQLProfileIT extends BaseGraphQLIT {

    @Before
    public void setUp() throws InterruptedException {
        removeItems(Profile.class);
    }

    @Test
    public void testGetProfile_WithoutCreation() throws Exception {
        try (CloseableHttpResponse response = post("graphql/profile/get-profile-without-creation.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNull(context.getValue("data.cdp.getProfile"));
        }
    }

    @Test
    public void testGetProfile_WithCreation() throws Exception {
        try (CloseableHttpResponse response = post("graphql/profile/get-profile-with-creation.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("profile_id", context.getValue("data.cdp.getProfile.cdp_profileIDs[0].id"));
        }
    }

    @Test
    public void testDeleteProfile() throws Exception {
        final Profile profile = new Profile("profile_id_to_delete");
        profile.setProperty("firstName", "TestFirstName");
        profile.setProperty("lastName", "TestLastName");
        profileService.save(profile);

        keepTrying("Failed waiting for the creation of the profile for the \"testCreateSegmentAndApplyToProfile\" test",
                () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/profile/delete-profile.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.deleteProfile"));
        }
    }

    @Test
    public void testFindProfiles() throws Exception {
        final Profile profile = new Profile("FindProfiles_ProfileId1");
        profile.setProperty("firstName", "FindProfiles_Username1");
        profileService.save(profile);
        refreshPersistence(Profile.class);

        try (CloseableHttpResponse response = post("graphql/profile/find-profiles.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(profile.getItemId(), context.getValue("data.cdp.findProfiles.edges[0].node.cdp_profileIDs[0].id"));
        }
    }

    @Test
    public void testDeleteAllPersonalData() throws Exception {
        final Profile profile = new Profile("profileId_deleteAllPersonalDataTest");
        profile.setProperty("firstName", "FirstName");
        profileService.save(profile);
        refreshPersistence(Profile.class);

        try (CloseableHttpResponse response = post("graphql/profile/delete-all-personal-data.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.deleteAllPersonalData"));
        }
    }

}
