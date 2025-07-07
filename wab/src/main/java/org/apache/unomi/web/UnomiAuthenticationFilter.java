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

import org.apache.unomi.api.services.ExecutionContextManager;
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
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String UNOMI_API_KEY_HEADER = "X-Unomi-Api-Key";

    private static final Logger logger = LoggerFactory.getLogger(UnomiAuthenticationFilter.class);

    private static final Set<String> PUBLIC_ENDPOINTS = new HashSet<>();
    static {
        PUBLIC_ENDPOINTS.add("/context.js");
        PUBLIC_ENDPOINTS.add("/context.json");
        PUBLIC_ENDPOINTS.add("/eventcollector");
    }

    private ExecutionContextManager executionContextManager;
    private TenantService tenantService;

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
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
            String apiKey = httpRequest.getHeader(UNOMI_API_KEY_HEADER);

            Tenant tenant = tenantService.getTenantByApiKey(apiKey, ApiKey.ApiKeyType.PUBLIC);
            if (tenant != null) {
                // Set tenant ID for the request
                executionContextManager.executeAsTenant(tenant.getItemId(), () -> {
                    try {
                        chain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        throw new RuntimeException(e);
                    }
                });
                return;
            }
            
            // If no public key found, try private key authentication for public endpoints
            // Private keys have higher privileges and should be able to access public endpoints
            String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);
            if (authHeader != null && authHeader.startsWith(BASIC_AUTH_PREFIX)) {
                String[] credentials = extractCredentials(authHeader);
                if (credentials != null && credentials.length == 2) {
                    String tenantId = credentials[0];
                    String privateKey = credentials[1];

                    if (tenantService.validateApiKeyWithType(tenantId, privateKey, ApiKey.ApiKeyType.PRIVATE)) {
                        // Set tenant ID for the request
                        executionContextManager.executeAsTenant(tenantId, () -> {
                            try {
                                chain.doFilter(request, response);
                            } catch (IOException | ServletException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        return;
                    }
                }
            }
            
            // If no valid authentication found, return 401
            httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"cxs\"");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        // Handle all other endpoints requests
        else {
            String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);
            if (authHeader == null || !authHeader.startsWith(BASIC_AUTH_PREFIX)) {
                httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"cxs\"");
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
            executionContextManager.executeAsTenant(tenantId, () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::endsWith);
    }

    private String[] extractCredentials(String authHeader) {
        try {
            String base64Credentials = authHeader.substring(BASIC_AUTH_PREFIX.length()).trim();
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
