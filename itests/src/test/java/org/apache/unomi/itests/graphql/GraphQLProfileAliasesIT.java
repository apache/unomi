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
import org.apache.unomi.api.ProfileAlias;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GraphQLProfileAliasesIT extends BaseGraphQLIT {

    @Before
    public void setUp() throws InterruptedException {
        removeItems(ProfileAlias.class);
        removeItems(Profile.class);
    }

    @Test
    public void lifecycle() throws Exception {
        final Profile profile = new Profile("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6");
        profileService.save(profile);

        // test adding an alias to a profile
        try (CloseableHttpResponse response = post("graphql/profileAlias/addAliasToProfile.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.addAliasToProfile"));
            Assert.assertEquals("myAlias", context.getValue("data.cdp.addAliasToProfile.alias"));
            Assert.assertNotNull(context.getValue("data.cdp.addAliasToProfile.profileID"));
            Assert.assertEquals("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6",
                    context.getValue("data.cdp.addAliasToProfile.profileID.id"));
            Assert.assertNotNull(context.getValue("data.cdp.addAliasToProfile.profileID.client"));
            Assert.assertEquals("facebook", context.getValue("data.cdp.addAliasToProfile.profileID.client.ID"));
        }

        // test fetching a profile by an alias
        try (CloseableHttpResponse response = post("graphql/profileAlias/getProfileAlias.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.getProfileAlias"));
            Assert.assertEquals("myAlias", context.getValue("data.cdp.getProfileAlias.alias"));
            Assert.assertNotNull(context.getValue("data.cdp.getProfileAlias.profileID"));
            Assert.assertEquals("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6",
                    context.getValue("data.cdp.getProfileAlias.profileID.id"));
        }

        // test fetching a profile's aliases
        try (CloseableHttpResponse response = post("graphql/profileAlias/getProfileAliases.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.getProfileAliases"));
            Assert.assertEquals("myAlias", context.getValue("data.cdp.getProfileAliases[0].alias"));
            Assert.assertNotNull(context.getValue("data.cdp.getProfileAliases[0].profileID"));
            Assert.assertEquals("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6",
                    context.getValue("data.cdp.getProfileAliases[0].profileID.id"));
        }

        // test filtering a profile aliases by criteria
        try (CloseableHttpResponse response = post("graphql/profileAlias/findProfileAliases.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.findProfileAliases"));
            Assert.assertEquals("myAlias", context.getValue("data.cdp.findProfileAliases.edges[0].node.alias"));
            Assert.assertNotNull(context.getValue("data.cdp.findProfileAliases.edges[0].node.profileID"));
            Assert.assertEquals("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6",
                    context.getValue("data.cdp.findProfileAliases.edges[0].node.profileID.id"));
        }

        // test removing an alias from a profile
        try (CloseableHttpResponse response = post("graphql/profileAlias/removeAliasFromProfile.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.removeAliasFromProfile"));
            Assert.assertEquals("myAlias", context.getValue("data.cdp.removeAliasFromProfile.alias"));
            Assert.assertNotNull(context.getValue("data.cdp.removeAliasFromProfile.profileID"));
            Assert.assertEquals("f6c1c5a0-eff3-42b7-b375-44da5d01f2a6",
                    context.getValue("data.cdp.removeAliasFromProfile.profileID.id"));
        }
    }
}
