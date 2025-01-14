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

import org.apache.unomi.api.tenants.TenantService;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TenantSecurityFilter implements Filter {

    private TenantService tenantService;

    private String defaultTenantId = "default";

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String tenantApiKey = httpRequest.getHeader("X-Unomi-Tenant-API-Key");

        if (tenantApiKey != null) {
            handleTenantRequest(tenantApiKey, httpRequest, response, chain);
        } else {
            handleLegacyRequest(httpRequest, response, chain);
        }
    }

    @Override
    public void destroy() {

    }

    private void handleTenantRequest(String apiKey, HttpServletRequest request,
            ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            String[] keyParts = apiKey.split(":");
            if (keyParts.length != 2) {
                ((HttpServletResponse) response).sendError(
                    HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key format");
                return;
            }

            String tenantId = keyParts[0];
            String key = keyParts[1];

            if (tenantService.validateApiKey(tenantId, key)) {
                tenantService.setCurrentTenant(tenantId);
                chain.doFilter(request, response);
            } else {
                ((HttpServletResponse) response).sendError(
                    HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            }
        } finally {
            tenantService.setCurrentTenant(null);
        }
    }

    private void handleLegacyRequest(HttpServletRequest request,
            ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            tenantService.setCurrentTenant(defaultTenantId);
            chain.doFilter(request, response);
        } finally {
            tenantService.setCurrentTenant(null);
        }
    }
}
