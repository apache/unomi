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

import org.apache.unomi.api.services.AdminCorsService;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeEvent;
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Operator allow-list of origins that may use credentialed CORS against admin REST and GraphQL.
 * Published by {@code WebConfig} through {@link ConfigSharingService}; parsed when set, not per request.
 */
@Component(service = {AdminCorsSettings.class, AdminCorsService.class, ConfigChangeListener.class})
public class AdminCorsSettings implements AdminCorsService, ConfigChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCorsSettings.class);

    /** ConfigSharingService key published by WebConfig from org.apache.unomi.admin.cors.allowedOrigins. */
    public static final String ALLOWED_ORIGINS_PROPERTY = "adminCorsAllowedOrigins";

    @Reference
    private ConfigSharingService configSharingService;

    private volatile PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(null);

    /** Used by OSGi Declarative Services. */
    public AdminCorsSettings() {
    }

    /** For tests. */
    public AdminCorsSettings(String allowedOrigins) {
        this.policy = PublicTrackerCorsPolicy.parse(allowedOrigins);
    }

    @Activate
    void activate() {
        reload();
    }

    @Override
    public void configChanged(ConfigChangeEvent event) {
        if (ALLOWED_ORIGINS_PROPERTY.equals(event.getName())) {
            reload();
        }
    }

    private synchronized void reload() {
        List<String> warnings = new ArrayList<>();
        Object raw = configSharingService.getProperty(ALLOWED_ORIGINS_PROPERTY);
        String configured = raw == null ? null : String.valueOf(raw);
        PublicTrackerCorsPolicy next = PublicTrackerCorsPolicy.parse(configured, false, warnings);
        for (String warning : warnings) {
            LOGGER.warn(warning);
        }
        this.policy = next;
    }

    @Override
    public boolean allows(String origin) {
        return policy.allows(origin);
    }
}
