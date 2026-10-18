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
package org.apache.unomi.rest.cors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTrackerCorsFilterTest {

    @Mock
    private ContainerRequestContext request;
    @Mock
    private ContainerResponseContext response;
    @Mock
    private UriInfo uriInfo;

    private PublicTrackerCorsFilter filter;
    private MultivaluedMap<String, Object> headers;

    @BeforeEach
    void setUp() {
        listed(null);
        headers = new MultivaluedHashMap<>();
        lenient().when(request.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getPath()).thenReturn("context.json");
        lenient().when(response.getHeaders()).thenReturn(headers);
    }

    /** What CXF's filter puts on a public tracker response: any origin, no credentials. */
    private void grantedByCxf() {
        headers.putSingle("Access-Control-Allow-Origin", "*");
        headers.putSingle("Access-Control-Allow-Credentials", Boolean.FALSE);
    }

    private void listed(String origins) {
        filter = new PublicTrackerCorsFilter(new PublicTrackerSettings(origins, null));
    }

    @Test
    void listedOriginNamesOnlyThatOriginAndVaries() {
        listed("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        grantedByCxf();

        filter.restrictCredentials(request, response);

        assertEquals("https://shop.example", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("true", headers.getFirst("Access-Control-Allow-Credentials"));
        assertEquals("Origin", headers.getFirst("Vary"));
    }

    @Test
    void listedOriginPreflightAnsweredByCxfGetsCredentials() {
        // CXF aborts the preflight with its own response; response filters still run on it.
        listed("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        grantedByCxf();
        headers.putSingle("Access-Control-Allow-Methods", "POST");
        headers.putSingle("Access-Control-Allow-Headers", "content-type,x-unomi-api-key,x-unomi-tenant-id");

        filter.restrictCredentials(request, response);

        assertEquals("https://shop.example", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("true", headers.getFirst("Access-Control-Allow-Credentials"));
        assertEquals("content-type,x-unomi-api-key,x-unomi-tenant-id", headers.getFirst("Access-Control-Allow-Headers"));
    }

    @Test
    void existingVaryIsKeptAndOriginAddedOnce() {
        listed("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        grantedByCxf();
        headers.add("Vary", "Accept-Encoding");

        filter.restrictCredentials(request, response);
        filter.restrictCredentials(request, response);

        assertEquals(java.util.Arrays.asList("Accept-Encoding", "Origin"), headers.get("Vary"));
    }

    @Test
    void unlistedOriginDoesNotGetCredentials() {
        listed("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://other.example");
        headers.putSingle("Access-Control-Allow-Origin", "https://other.example");
        headers.putSingle("Access-Control-Allow-Credentials", "true");

        filter.restrictCredentials(request, response);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertFalse(headers.containsKey("Access-Control-Allow-Credentials"));
    }

    @Test
    void defaultSettingsAllowNoCredentials() {
        listed(null);
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        grantedByCxf();

        filter.restrictCredentials(request, response);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertNull(headers.getFirst("Access-Control-Allow-Credentials"));
    }

    @Test
    void responseCxfDidNotGrantIsLeftAlone() {
        // For example a failed preflight: CXF adds no CORS headers, and neither do we.
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");

        filter.restrictCredentials(request, response);

        assertTrue(headers.isEmpty());
    }

    @Test
    void contextJsIsAPublicTrackerPath() {
        when(uriInfo.getPath()).thenReturn("context.js");
        listed("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        grantedByCxf();

        filter.restrictCredentials(request, response);

        assertEquals("https://shop.example", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("true", headers.getFirst("Access-Control-Allow-Credentials"));
    }

    @Test
    void nonPublicPathIsLeftAlone() {
        when(uriInfo.getPath()).thenReturn("profiles/search");
        grantedByCxf();

        filter.restrictCredentials(request, response);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals(Boolean.FALSE, headers.getFirst("Access-Control-Allow-Credentials"));
    }

    @Test
    void inheritsCxfPreMatchingAndPriority() {
        // CXF reads both through the class hierarchy; the rewrite then needs no ordering of its own.
        assertTrue(org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter.class.isAssignableFrom(PublicTrackerCorsFilter.class));
        assertNotNull(org.apache.cxf.jaxrs.utils.AnnotationUtils.getClassAnnotation(
                PublicTrackerCorsFilter.class, javax.ws.rs.container.PreMatching.class));
        assertEquals(org.apache.cxf.jaxrs.utils.AnnotationUtils.getBindingPriority(
                        org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter.class),
                org.apache.cxf.jaxrs.utils.AnnotationUtils.getBindingPriority(PublicTrackerCorsFilter.class));
    }

    @Test
    void requestWithoutOriginIsLeftAlone() {
        when(request.getHeaderString("Origin")).thenReturn(null);

        filter.restrictCredentials(request, response);

        assertFalse(headers.containsKey("Access-Control-Allow-Origin"));
        assertFalse(headers.containsKey("Access-Control-Allow-Credentials"));
    }
}
