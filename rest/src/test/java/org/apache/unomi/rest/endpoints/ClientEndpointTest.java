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
package org.apache.unomi.rest.endpoints;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The "download my data" endpoint must read the cookie through the navigation-aware lookup, so that a
 * plain link on the website keeps the visitor (UNOMI-1005).
 */
@ExtendWith(MockitoExtension.class)
class ClientEndpointTest {

    @Mock private ProfileService profileService;
    @Mock private ConfigSharingService configSharingService;
    @Mock private RestServiceUtils restServiceUtils;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private ClientEndpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        endpoint = new ClientEndpoint();
        set("profileService", profileService);
        set("configSharingService", configSharingService);
        set("restServiceUtils", restServiceUtils);
        set("request", request);
        set("response", response);
        when(configSharingService.getProperty("allowedProfileDownloadFormats")).thenReturn("csv,yaml,json,text");
    }

    @Test
    void downloadUsesTheNavigationAwareCookieLookup() throws Exception {
        Profile profile = new Profile("cookie-profile");
        when(restServiceUtils.getProfileIdCookieValueForNavigation(request)).thenReturn("cookie-profile");
        when(profileService.load("cookie-profile")).thenReturn(profile);

        Response answer = endpoint.getClient("myprofile", "json");

        assertEquals(200, answer.getStatus());
        verify(response).setHeader("Content-Disposition", "attachment; filename=\"my-profile.json\"");
        verify(restServiceUtils, never()).getProfileIdCookieValue(any());
    }

    @Test
    void refusedCookieAnswersNotFound() {
        when(restServiceUtils.getProfileIdCookieValueForNavigation(request)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> endpoint.getClient("myprofile", "json"));
    }

    private void set(String name, Object value) throws Exception {
        Field field = ClientEndpoint.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(endpoint, value);
    }
}
