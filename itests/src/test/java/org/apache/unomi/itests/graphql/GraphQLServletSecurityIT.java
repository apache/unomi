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
import org.junit.Assert;
import org.junit.Test;

public class GraphQLServletSecurityIT extends BaseGraphQLIT {

    @Test
    public void testAnonymousProcessEventsRequest() throws Exception {
        try (CloseableHttpResponse response = postWithAuthType("graphql/security/process-events.json", AuthType.PUBLIC_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            Assert.assertNotNull(context.getValue("data.cdp.processEvents"));
        }
    }

    @Test
    public void testAnonymousGetProfileRequest() throws Exception {
        try (CloseableHttpResponse response = postWithAuthType("graphql/security/get-profile.json", AuthType.PUBLIC_KEY)) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            Assert.assertNull(context.getValue("data.cdp.getProfile"));
        }
    }

    @Test
    public void testAnonymousGetSegmentRequest() throws Exception {
        try (CloseableHttpResponse response = postAnonymous("graphql/security/get-segment.json")) {

            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAnonymousGetEventRequest() throws Exception {
        try (CloseableHttpResponse response = postAnonymous("graphql/security/get-event.json")) {

            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAuthenticatedWrongGetEventRequest() throws Exception {
        try (CloseableHttpResponse response = postAs("graphql/security/get-event.json", "karaf", "wrongPassword")) {

            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAuthenticatedGetEventRequest() throws Exception {
        try (CloseableHttpResponse response = post("graphql/security/get-event.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            Assert.assertNull(context.getValue("data.cdp.getEvent"));
        }
    }

    @Test
    public void testAnonymousSubscriptionRequest() throws Exception {
        try (CloseableHttpResponse response = postAnonymous("graphql/security/subscribe.json")) {

            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

}
