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

    private static final List<Pattern> PUBLIC_PATH_PATTERNS = Arrays.asList(
            Pattern.compile("(GET|POST|OPTIONS) context\\.js(on|)"),
            Pattern.compile("(GET|POST|OPTIONS) eventcollector"),
            Pattern.compile("(GET|OPTIONS) client/.*")
    );


    private static final Map<String, String> ROLES_MAPPING;

    static {
        Map<String, String> roles = new HashMap<>();
        roles.put("org.apache.unomi.rest.endpoints.ContextJsonEndpoint.contextJSAsGet", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ContextJsonEndpoint.contextJSAsPost", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ContextJsonEndpoint.contextJSONAsGet", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ContextJsonEndpoint.contextJSONAsPost", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ContextJsonEndpoint.options", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.EventsCollectorEndpoint.collectAsGet", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.EventsCollectorEndpoint.collectAsPost", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.EventsCollectorEndpoint.options", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ClientEndpoint.getClient", GUEST_ROLES);
        roles.put("org.apache.unomi.rest.endpoints.ClientEndpoint.options", GUEST_ROLES);
        ROLES_MAPPING = Collections.unmodifiableMap(roles);
    }

    @Override
    public List<Pattern> getPublicPathPatterns() {
        return PUBLIC_PATH_PATTERNS;
    }

    @Override
    public Map<String, String> getMethodRolesMap() {
        return ROLES_MAPPING;
    }

    @Override
    public String getGlobalRoles() {
        return ADMIN_ROLES;
    }
}
