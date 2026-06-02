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
import org.apache.unomi.lists.UserList;
import org.junit.Assert;
import org.junit.Test;
import java.util.Objects;

public class GraphQLListIT extends BaseGraphQLIT {

    @Test
    public void testCRUD() throws Exception {
        Profile persistedProfile = null;

        try {
            final Profile profile = new Profile("test_profile_id");
            profile.setProperty("firstName", "TestFirstName");
            profile.setProperty("lastName", "TestLastName");

            persistedProfile = profileService.save(profile);

            refreshPersistence(Profile.class);

            keepTrying("Failed waiting for the creation of the profile",
                    () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);

            try (CloseableHttpResponse response = post("graphql/list/create-list.json")) {
                final ResponseContext context = ResponseContext.parse(response.getEntity());

                Assert.assertEquals("testListId", context.getValue("data.cdp.createOrUpdateList.id"));
                Assert.assertEquals("testListName", context.getValue("data.cdp.createOrUpdateList.name"));
                Assert.assertEquals("testSite", context.getValue("data.cdp.createOrUpdateList.view.name"));
            }

            refreshPersistence(UserList.class);

            try (CloseableHttpResponse response = post("graphql/list/update-list.json")) {
                final ResponseContext context = ResponseContext.parse(response.getEntity());

                Assert.assertEquals("testListId", context.getValue("data.cdp.createOrUpdateList.id"));
                Assert.assertEquals("testListNameUpdated", context.getValue("data.cdp.createOrUpdateList.name"));
                Assert.assertEquals("testSiteUpdated", context.getValue("data.cdp.createOrUpdateList.view.name"));
            }

            refreshPersistence(UserList.class);

            try (CloseableHttpResponse response = post("graphql/list/get-list.json")) {
                final ResponseContext context = ResponseContext.parse(response.getEntity());

                Assert.assertEquals("testListId", context.getValue("data.cdp.getList.id"));
                Assert.assertEquals("testListNameUpdated", context.getValue("data.cdp.getList.name"));
                Assert.assertEquals("testSiteUpdated", context.getValue("data.cdp.getList.view.name"));
            }

            try (CloseableHttpResponse response = post("graphql/list/add-profile-to-list.json")) {
                final ResponseContext context = ResponseContext.parse(response.getEntity());

                Assert.assertEquals("testListId", context.getValue("data.cdp.addProfileToList.id"));
            }

            refreshPersistence(UserList.class);

            final ResponseContext findListsContext = keepTrying("Failed waiting for profile in list query",
                    () -> {
                        try (CloseableHttpResponse response = post("graphql/list/find-lists.json")) {
                            return ResponseContext.parse(response.getEntity());
                        } catch (Exception e) {
                            return null;
                        }
                    },
                    context -> {
                        if (context == null) {
                            return false;
                        }
                        Integer totalCount = (Integer) context.getValue("data.cdp.findLists.totalCount");
                        if (totalCount == null || totalCount != 1) {
                            return false;
                        }
                        Object profileId = context.getValue("data.cdp.findLists.edges[0].node.active.edges[0].node.cdp_profileIDs[0].id");
                        return profile.getItemId().equals(profileId);
                    },
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            Assert.assertEquals("testListId", findListsContext.getValue("data.cdp.findLists.edges[0].node.id"));

            try (CloseableHttpResponse response = post("graphql/list/delete-list.json")) {
                final ResponseContext context = ResponseContext.parse(response.getEntity());

                Assert.assertTrue("deleteList should return true", Boolean.TRUE.equals(context.getValue("data.cdp.deleteList")));
            }
        } finally {
            if (persistedProfile != null) {
                profileService.delete(persistedProfile.getItemId(), false);
            }
        }
    }

}
