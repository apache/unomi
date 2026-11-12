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
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeEvent;
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeEvent.ConfigChangeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCorsSettingsTest {

    private static final String ADMIN = "https://admin.example";

    @Mock
    private ConfigSharingService configSharingService;

    private AdminCorsSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        settings = new AdminCorsSettings();
        Field field = AdminCorsSettings.class.getDeclaredField("configSharingService");
        field.setAccessible(true);
        field.set(settings, configSharingService);
    }

    /** The shipped default is empty: no origin gets credentialed admin CORS. */
    @Test
    void emptyByDefault() {
        when(configSharingService.getProperty(AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("");
        settings.activate();

        assertFalse(settings.allows(ADMIN));
        assertFalse(settings.allows(null));
    }

    /** WebConfig may publish the setting after this component activates; the change event picks it up. */
    @Test
    void picksUpLaterPublishedOrigins() {
        when(configSharingService.getProperty(AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn(null, ADMIN);
        settings.activate();
        assertFalse(settings.allows(ADMIN));

        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.ADDED,
                AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY, null, ADMIN));

        assertTrue(settings.allows(ADMIN));
        assertFalse(settings.allows("https://evil.example"));
    }

    /** Removing an origin from the list revokes it without a restart. */
    @Test
    void revokesRemovedOrigins() {
        when(configSharingService.getProperty(AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn(ADMIN, "");
        settings.activate();
        assertTrue(settings.allows(ADMIN));

        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED,
                AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY, ADMIN, ""));

        assertFalse(settings.allows(ADMIN));
    }

    /** Other settings changing does not reload (or reset) the admin list. */
    @Test
    void ignoresUnrelatedChanges() {
        when(configSharingService.getProperty(AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn(ADMIN);
        settings.activate();

        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED,
                "publicCorsAllowedOrigins", null, "https://shop.example"));

        assertTrue(settings.allows(ADMIN));
    }

    /** Wildcards and malformed entries are dropped, never treated as match-all. */
    @Test
    void rejectsWildcardsAndMalformedEntries() {
        when(configSharingService.getProperty(AdminCorsSettings.ALLOWED_ORIGINS_PROPERTY))
                .thenReturn("*, admin.example, https://admin.example/path, " + ADMIN);
        settings.activate();

        assertTrue(settings.allows(ADMIN));
        assertFalse(settings.allows("https://evil.example"));
        assertFalse(settings.allows("null"));
    }
}
