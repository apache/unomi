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

import org.apache.unomi.api.services.ConfigSharingService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTrackerCorsFilterTest {

    @Mock
    private ConfigSharingService configSharingService;
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
        filter = new PublicTrackerCorsFilter(configSharingService);
        headers = new MultivaluedHashMap<>();
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("context.json");
    }

    @Test
    void listedOriginNamesOnlyThatOriginAndVaries() {
        when(configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY))
                .thenReturn("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);

        assertEquals("https://shop.example", headers.getFirst("Access-Control-Allow-Origin"));
        assertEquals("true", headers.getFirst("Access-Control-Allow-Credentials"));
        assertEquals("Origin", headers.getFirst("Vary"));
    }

    @Test
    void listedOriginPreflightDoesTheSame() throws Exception {
        when(configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY))
                .thenReturn("https://shop.example");
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");

        filter.filter(request);

        verify(request).abortWith(org.mockito.ArgumentMatchers.argThat(response -> {
            MultivaluedMap<String, Object> preflight = response.getHeaders();
            return "https://shop.example".equals(preflight.getFirst("Access-Control-Allow-Origin"))
                    && "true".equals(preflight.getFirst("Access-Control-Allow-Credentials"))
                    && "Origin".equals(String.valueOf(preflight.getFirst("Vary")));
        }));
    }

    @Test
    void unlistedOriginDoesNotGetCredentials() {
        when(configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY))
                .thenReturn("https://shop.example");
        when(request.getHeaderString("Origin")).thenReturn("https://other.example");
        when(response.getHeaders()).thenReturn(headers);
        headers.putSingle("Access-Control-Allow-Credentials", "true");

        filter.filter(request, response);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertFalse(headers.containsKey("Access-Control-Allow-Credentials"));
    }

    @Test
    void defaultSettingsAllowNoCredentials() {
        when(configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY)).thenReturn(null);
        when(request.getHeaderString("Origin")).thenReturn("https://shop.example");
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);

        assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
        assertNull(headers.getFirst("Access-Control-Allow-Credentials"));
    }

    @Test
    void requestWithoutOriginIsLeftAlone() {
        when(request.getHeaderString("Origin")).thenReturn(null);
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(request, response);

        assertFalse(headers.containsKey("Access-Control-Allow-Origin"));
        assertFalse(headers.containsKey("Access-Control-Allow-Credentials"));
    }
}
