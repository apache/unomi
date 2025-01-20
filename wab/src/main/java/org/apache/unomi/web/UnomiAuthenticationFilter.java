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
package org.apache.unomi.web;

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class UnomiAuthenticationFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(UnomiAuthenticationFilter.class);

    private static final Set<String> PUBLIC_ENDPOINTS = new HashSet<>();
    static {
        PUBLIC_ENDPOINTS.add("/context.js");
        PUBLIC_ENDPOINTS.add("/context.json");
        PUBLIC_ENDPOINTS.add("/eventcollector");
    }

    private TenantService tenantService;

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        // Handle public endpoints
        if (isPublicEndpoint(path)) {
            String apiKey = httpRequest.getHeader("X-Unomi-API-Key");
            if (apiKey == null) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing public API key");
                return;
            }

            Tenant tenant = tenantService.getTenantByApiKey(apiKey, ApiKey.ApiKeyType.PUBLIC);
            if (tenant == null) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid public API key");
                return;
            }

            // Set tenant ID for the request
            tenantService.setCurrentTenant(tenant.getItemId());
            try {
                chain.doFilter(request, response);
            } finally {
                tenantService.setCurrentTenant(null);
            }
        }
        // Handle private endpoints (CXS)
        else {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"Apache Unomi\"");
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return;
            }

            String[] credentials = extractCredentials(authHeader);
            if (credentials == null || credentials.length != 2) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials format");
                return;
            }

            String tenantId = credentials[0];
            String privateKey = credentials[1];

            if (!tenantService.validateApiKeyWithType(tenantId, privateKey, ApiKey.ApiKeyType.PRIVATE)) {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant ID or private API key");
                return;
            }

            // Set tenant ID for the request
            tenantService.setCurrentTenant(tenantId);
            try {
                chain.doFilter(request, response);
            } finally {
                tenantService.setCurrentTenant(null);
            }
        }
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::endsWith);
    }

    private String[] extractCredentials(String authHeader) {
        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            return credentials.split(":", 2);
        } catch (Exception e) {
            logger.warn("Failed to decode authentication header", e);
            return null;
        }
    }

    @Override
    public void destroy() {
    }
}
