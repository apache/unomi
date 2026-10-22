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

import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.apache.unomi.api.security.TenantPrincipal;

import javax.security.auth.Subject;
import java.security.Principal;

/**
 * The tenant the current REST request authenticated as. It is read from the CXF security context that the
 * authentication filter stores on the request message, which stays in place until the response has been
 * written, unlike the thread-local security context that is cleared before the last response filters run.
 */
public final class RequestTenant {

    private RequestTenant() {
    }

    /**
     * @return the tenant id of the current request, or {@code null} outside a request or when the caller is
     *         not a tenant (anonymous, or a system administrator without a tenant)
     */
    public static String id() {
        return idOf(JAXRSUtils.getCurrentMessage());
    }

    /**
     * In a response filter the current message is the response; the request message is read from the
     * exchange.
     */
    static String idOf(Message message) {
        if (message == null) {
            return null;
        }
        Object context = message.get(SecurityContext.class);
        if (context == null && message.getExchange() != null && message.getExchange().getInMessage() != null) {
            context = message.getExchange().getInMessage().get(SecurityContext.class);
        }
        if (!(context instanceof RolePrefixSecurityContextImpl)) {
            return null;
        }
        Subject subject = ((RolePrefixSecurityContextImpl) context).getSubject();
        if (subject == null) {
            return null;
        }
        for (Principal principal : subject.getPrincipals()) {
            if (principal instanceof TenantPrincipal) {
                return ((TenantPrincipal) principal).getTenantId();
            }
        }
        return null;
    }
}
