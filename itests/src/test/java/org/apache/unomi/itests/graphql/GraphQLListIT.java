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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;
import org.junit.Assert;
import org.junit.Test;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;

public class GraphQLListIT extends BaseGraphQLIT {

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected UserListService userListService;

    @Test
    public void testCreateList() throws IOException {
        try (CloseableHttpResponse response = post("graphql/list/create-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testListId", context.getValue("data.cdp.createOrUpdateList.id"));
            Assert.assertEquals("testListName", context.getValue("data.cdp.createOrUpdateList.name"));
            Assert.assertEquals("testSite", context.getValue("data.cdp.createOrUpdateList.view.name"));
        }
    }

    @Test
    public void testUpdateList() throws Exception {
        final UserList userList = createList("listIdToUpdate", "listName", "listView");

        keepTrying("Failed waiting for the creation of the list",
                () -> userListService.load(userList.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/list/update-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("listIdToUpdate", context.getValue("data.cdp.createOrUpdateList.id"));
            Assert.assertEquals("listNameUpdated", context.getValue("data.cdp.createOrUpdateList.name"));
            Assert.assertEquals("testSiteUpdated", context.getValue("data.cdp.createOrUpdateList.view.name"));
        }
    }

    @Test
    public void testDeleteList() throws Exception {
        final UserList userList = createList("listIdToDelete", "listName", "listView");

        keepTrying("Failed waiting for the creation of the list",
                () -> userListService.load(userList.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/list/delete-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue("listIdToUpdate", context.getValue("data.cdp.deleteList"));
        }
    }

    @Test
    public void testGetList() throws Exception {
        final UserList userList = createList("myListId", "listName", "listView");

        keepTrying("Failed waiting for the creation of the list",
                () -> userListService.load(userList.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/list/get-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("myListId", context.getValue("data.cdp.getList.id"));
            Assert.assertEquals("listName", context.getValue("data.cdp.getList.name"));
            Assert.assertEquals("listView", context.getValue("data.cdp.getList.view.name"));
        }
    }

    @Test
    public void testAdd_Find_Remove_ProfileToList() throws Exception {
        final UserList userList = createList("addedToProfileTestListId", "addedToProfileTestListName", "addedToProfileTestListView");

        keepTrying("Failed waiting for the creation of the list",
                () -> userListService.load(userList.getItemId()), Objects::nonNull, 1000, 100);

        final Profile profile = new Profile("test_profile_id");
        profile.setProperty("firstName", "TestFirstName");
        profile.setProperty("lastName", "TestLastName");
        profileService.save(profile);

        keepTrying("Failed waiting for the creation of the profile",
                () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/list/add-profile-to-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(userList.getItemId(), context.getValue("data.cdp.addProfileToList.id"));
            Assert.assertEquals(userList.getMetadata().getName(), context.getValue("data.cdp.addProfileToList.name"));
            Assert.assertEquals(userList.getMetadata().getScope(), context.getValue("data.cdp.addProfileToList.view.name"));
        }

        Thread.sleep(5000);

        try (CloseableHttpResponse response = post("graphql/list/find-lists.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(1, ((Integer) context.getValue("data.cdp.findLists.totalCount")).intValue());
            Assert.assertEquals(userList.getItemId(), context.getValue("data.cdp.findLists.edges[0].node.id"));
            Assert.assertEquals(userList.getMetadata().getName(), context.getValue("data.cdp.findLists.edges[0].node.name"));
            Assert.assertEquals(userList.getMetadata().getScope(), context.getValue("data.cdp.findLists.edges[0].node.view.name"));
            Assert.assertEquals(profile.getItemId(), context.getValue("data.cdp.findLists.edges[0].node.active.edges[0].node.cdp_profileIDs[0].id"));
        }

        try (CloseableHttpResponse response = post("graphql/list/remove-profile-from-list.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.removeProfileFromList"));
        }
    }

    private UserList createList(final String id, final String name, final String scope) {
        final Metadata metadata = new Metadata();

        metadata.setId(id);
        metadata.setName(name);
        metadata.setScope(scope);

        final UserList userList = new UserList();

        userList.setItemType(UserList.ITEM_TYPE);
        userList.setMetadata(metadata);

        userListService.save(userList);

        return userList;
    }

}
