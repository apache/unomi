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
import org.apache.unomi.api.segments.Segment;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.Objects;
import java.util.UUID;

public class GraphQLSegmentIT extends BaseGraphQLIT {

    @Before
    public void setUp() throws InterruptedException {
        removeItems(Segment.class);
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Segment.class);
    }

    @Test
    public void testCreateThenGetAndDeleteSegment() throws Exception {
        try (CloseableHttpResponse response = postWithAuthType("graphql/segment/create-or-update-segment.json", AuthType.PRIVATE_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testSegment", context.getValue("data.cdp.createOrUpdateSegment.id"));
            Assert.assertEquals("testSegment", context.getValue("data.cdp.createOrUpdateSegment.name"));
            Assert.assertEquals("http://www.domain.com", context.getValue("data.cdp.createOrUpdateSegment.view.name"));
        }

        refreshPersistence(Segment.class);

        try (CloseableHttpResponse response = postWithAuthType("graphql/segment/get-segment.json", AuthType.PRIVATE_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testSegment", context.getValue("data.cdp.getSegment.id"));
            Assert.assertEquals("testSegment", context.getValue("data.cdp.getSegment.name"));
            Assert.assertNotNull(context.getValue("data.cdp.getSegment.filter"));
        }

        try (CloseableHttpResponse response = postWithAuthType("graphql/segment/delete-segment.json", AuthType.PRIVATE_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.deleteSegment"));
        }
    }

    @Test
    public void testCreateSegmentAndApplyToProfile() throws Exception {
        final Profile profile = new Profile(UUID.randomUUID().toString());
        profile.setProperty("firstName", "TestFirstName");
        profile.setProperty("lastName", "TestLastName");
        profileService.save(profile);

        keepTrying("Failed waiting for the creation of the profile for the \"testCreateSegmentAndApplyToProfile\" test",
                () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);

        refreshPersistence(Segment.class);

        try (CloseableHttpResponse response = postWithAuthType("graphql/segment/create-segment-with-properties-filter.json",  AuthType.PRIVATE_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("simpleSegment", context.getValue("data.cdp.createOrUpdateSegment.id"));
            Assert.assertEquals("simpleSegment", context.getValue("data.cdp.createOrUpdateSegment.name"));
            Assert.assertEquals("http://www.domain.com", context.getValue("data.cdp.createOrUpdateSegment.view.name"));

            keepTrying("Failed waiting for the check segment for profile in scope of the \"testCreateSegmentAndApplyToProfile\" test",
                    () -> profileService.load(profile.getItemId()),
                    p -> p.getSegments() != null && p.getSegments().contains("simpleSegment"), 1000, 100);
        }
    }

}
