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
package org.apache.unomi.web.servlets;

import org.apache.unomi.api.services.ConfigSharingService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebConfigTest {

    @Test
    void cookieFlagsArePublishedAsTheConfiguredText() throws Exception {
        // As booleans, the framework would turn "flase" into false before Unomi could keep HttpOnly on.
        WebConfig.Config config = mock(WebConfig.Config.class);
        when(config.contextserver_profileIdCookieHttpOnly()).thenReturn("flase");
        when(config.public_cors_matchReferer()).thenReturn("yes");
        when(config.public_cors_tenantOrigins()).thenReturn("off");
        when(config.public_cors_allowedOrigins()).thenReturn("https://shop.example");
        when(config.contextserver_profileIdCookieSameSite()).thenReturn("None");
        ConfigSharingService configSharingService = mock(ConfigSharingService.class);

        WebConfig webConfig = new WebConfig();
        Field field = WebConfig.class.getDeclaredField("configSharingService");
        field.setAccessible(true);
        field.set(webConfig, configSharingService);
        webConfig.activate(config);

        verify(configSharingService).setProperty("profileIdCookieHttpOnly", "flase");
        verify(configSharingService).setProperty("publicCorsMatchReferer", "yes");
        verify(configSharingService).setProperty("publicCorsTenantOrigins", "off");
        // Origins before SameSite, so SameSite=None is never resolved against an empty list on the way.
        InOrder order = inOrder(configSharingService);
        order.verify(configSharingService).setProperty("publicCorsAllowedOrigins", "https://shop.example");
        order.verify(configSharingService).setProperty("publicCorsTenantOrigins", "off");
        order.verify(configSharingService).setProperty("profileIdCookieSameSite", "None");
    }
}
