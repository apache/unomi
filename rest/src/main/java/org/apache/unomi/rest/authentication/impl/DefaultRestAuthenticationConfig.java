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

import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Default implementation for the unomi authentication on Rest endpoints
 */
@Component(service = RestAuthenticationConfig.class, configurationPid = "org.apache.unomi.rest.authentication", immediate = true)
@Designate(ocd = DefaultRestAuthenticationConfig.Config.class)
public class DefaultRestAuthenticationConfig implements RestAuthenticationConfig {

    private static final String GUEST_ROLES = UnomiRoles.USER;
    private static final String ADMIN_ROLES = UnomiRoles.ADMINISTRATOR;
    private static final String TENANT_ADMIN_ROLES = UnomiRoles.ADMINISTRATOR + " " + UnomiRoles.TENANT_ADMINISTRATOR;

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
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.getTenants", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.getTenant", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.createTenant", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.updateTenant", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.deleteTenant", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.generateApiKey", ADMIN_ROLES);
        roles.put("org.apache.unomi.rest.tenants.TenantEndpoint.validateApiKey", ADMIN_ROLES);
        ROLES_MAPPING = Collections.unmodifiableMap(roles);
    }

    private volatile boolean v2CompatibilityModeEnabled = false;
    private volatile String v2CompatibilityDefaultTenantId = "default";

    @Activate
    public void activate(Map<String, Object> properties) {
        modified(properties);
    }

    @Modified
    public void modified(Map<String, Object> properties) {
        if (properties != null) {
            Object v2Mode = properties.get("v2CompatibilityModeEnabled");
            if (v2Mode != null) {
                this.v2CompatibilityModeEnabled = Boolean.parseBoolean(v2Mode.toString());
            }

            Object defaultTenant = properties.get("v2CompatibilityDefaultTenantId");
            if (defaultTenant != null) {
                this.v2CompatibilityDefaultTenantId = defaultTenant.toString();
            }
        }
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
        return TENANT_ADMIN_ROLES;
    }

    @Override
    public boolean isV2CompatibilityModeEnabled() {
        return v2CompatibilityModeEnabled;
    }

    @Override
    public String getV2CompatibilityDefaultTenantId() {
        return v2CompatibilityDefaultTenantId;
    }

    @ObjectClassDefinition(
        name = "Unomi REST Authentication Configuration",
        description = "Configuration for Unomi REST authentication including V2 compatibility mode"
    )
    public @interface Config {

        @AttributeDefinition(
            name = "V2 Compatibility Mode Enabled",
            description = "Enable V2 compatibility mode to allow V2 clients to use Unomi V3 without API keys"
        )
        boolean v2CompatibilityModeEnabled() default false;

        @AttributeDefinition(
            name = "V2 Compatibility Default Tenant ID",
            description = "Default tenant ID to use in V2 compatibility mode"
        )
        String v2CompatibilityDefaultTenantId() default "default";
    }
}
