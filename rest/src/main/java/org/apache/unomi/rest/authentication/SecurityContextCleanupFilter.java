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
package org.apache.unomi.rest.authentication;

import org.apache.unomi.api.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;

/**
 * Response filter that ensures the security context is always cleaned up after request processing
 */
@Priority(Priorities.USER + 1000)
public class SecurityContextCleanupFilter implements ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityContextCleanupFilter.class);
    private final SecurityService securityService;

    public SecurityContextCleanupFilter(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        try {
            securityService.clearCurrentSubject();
            if (logger.isDebugEnabled()) {
                logger.debug("Cleared security context after request processing");
            }
        } catch (Exception e) {
            logger.error("Error clearing security context", e);
        }
    }
}
