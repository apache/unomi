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
package org.apache.unomi.rest.authentication.impl;

import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.osgi.service.component.annotations.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Default implementation for the unomi authentication on Rest endpoints
 */
@Component(service = RestAuthenticationConfig.class)
public class DefaultRestAuthenticationConfig implements RestAuthenticationConfig {

    private static final String GUEST_ROLES = "ROLE_UNOMI_PUBLIC";
    private static final String ADMIN_ROLES = "ROLE_UNOMI_ADMIN";

    @Override
    public List<Pattern> getPublicPathPatterns() {
        List<Pattern> publicPaths = new ArrayList<>();
        publicPaths.add(Pattern.compile("(GET|POST|OPTIONS) context\\.json"));
        publicPaths.add(Pattern.compile("(GET|POST|OPTIONS) eventcollector"));
        publicPaths.add(Pattern.compile("GET client/.*"));
        return publicPaths;
    }

    @Override
    public Map<String, String> getMethodRolesMap() {
        Map<String, String> roleMappings = new HashMap<>();
        roleMappings.put("org.apache.unomi.rest.ContextJsonEndpoint.contextJSONAsGet", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.ContextJsonEndpoint.contextJSONAsPost", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.ContextJsonEndpoint.options", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.EventsCollectorEndpoint.collectAsGet", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.EventsCollectorEndpoint.collectAsPost", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.EventsCollectorEndpoint.options", GUEST_ROLES);
        roleMappings.put("org.apache.unomi.rest.ClientEndpoint.getClient", GUEST_ROLES);
        return roleMappings;
    }

    @Override
    public String getGlobalRoles() {
        return ADMIN_ROLES;
    }
}
