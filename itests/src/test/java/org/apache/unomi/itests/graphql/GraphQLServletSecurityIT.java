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
        // HTTP contrast to the WebSocket finding: anonymous subscription POST must stay 401.
        try (CloseableHttpResponse response = postAnonymous("graphql/security/subscribe.json")) {

            Assert.assertEquals(401, response.getStatusLine().getStatusCode());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Operation-authorization bypasses.
    //
    // Each payload below is a way a caller holding only the page-embedded public API key could try to
    // get a privileged operation executed. The public allow-list is getProfile and processEvents only,
    // so every one of these must be refused. They are written as end-to-end requests deliberately: the
    // decision has to hold against the real parser and the real servlet, not just in a unit test.
    // ---------------------------------------------------------------------------------------------

    /** An operation merely NAMED IntrospectionQuery must not be treated as introspection. */
    @Test
    public void testPublicKeyCannotRunPrivilegedOperationNamedIntrospectionQuery() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-introspection-named.json");
    }

    /** A privileged operation selected by operationName, hidden behind a benign first operation. */
    @Test
    public void testPublicKeyCannotSmugglePrivilegedOperationViaOperationName() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-operation-name-smuggle.json");
    }

    /** A leading fragment definition must not make the document look non-executable. */
    @Test
    public void testPublicKeyCannotBypassWithLeadingFragment() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-leading-fragment.json");
    }

    /** A privileged field hidden inside a fragment spread must still be seen. */
    @Test
    public void testPublicKeyCannotHidePrivilegedFieldInFragmentSpread() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-fragment-spread.json");
    }

    /** Several operations and no operationName: which one executes is ambiguous, so it must be refused. */
    @Test
    public void testPublicKeyCannotUseAmbiguousMultiOperationDocument() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-ambiguous-multi-operation.json");
    }

    /** An allowed field does not license a second root field alongside it. */
    @Test
    public void testPublicKeyCannotAddExtraRootFieldBesideAllowedOne() throws Exception {
        assertRefusedForPublicKey("graphql/security/bypass-extra-root-field.json");
    }

    /**
     * A public-key caller must not get privileged data out of this document. Refusal is either a 401 or
     * a 200 carrying no data — both are acceptable outcomes, what matters is that nothing privileged is
     * returned. Asserting only on the status code would let a 200-with-data regression pass unnoticed.
     */
    private void assertRefusedForPublicKey(final String resource) throws Exception {
        try (CloseableHttpResponse response = postWithAuthType(resource, AuthType.PUBLIC_KEY)) {
            final int status = response.getStatusLine().getStatusCode();
            if (status == 401) {
                return;
            }
            Assert.assertEquals("Expected the request to be refused (401) or to return no privileged data",
                    200, status);
            final ResponseContext context = ResponseContext.parse(response.getEntity());
            Assert.assertNull("A public API key must not be able to read profiles through " + resource,
                    context.getValue("data.cdp.findProfiles"));
        }
    }
}
