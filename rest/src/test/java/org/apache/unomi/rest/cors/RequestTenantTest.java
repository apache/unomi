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
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.security.SecurityContext;
import org.apache.unomi.api.security.TenantPrincipal;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTenantTest {

    @Test
    void theTenantIsFoundFromTheResponseMessageToo() {
        // The authentication filter stores the security context on the request; a response filter's current
        // message is the response, which only reaches the request through the exchange.
        Subject subject = new Subject();
        subject.getPrincipals().add(new TenantPrincipal("acme"));
        Message request = new MessageImpl();
        request.put(SecurityContext.class, new RolePrefixSecurityContextImpl(subject, "ROLE_", "prefix"));
        Message response = new MessageImpl();
        Exchange exchange = new ExchangeImpl();
        exchange.setInMessage(request);
        exchange.setOutMessage(response);
        request.setExchange(exchange);
        response.setExchange(exchange);

        assertEquals("acme", RequestTenant.idOf(request));
        assertEquals("acme", RequestTenant.idOf(response));
    }

    @Test
    void noTenantOutsideAnAuthenticatedTenantRequest() {
        assertNull(RequestTenant.idOf(null));
        assertNull(RequestTenant.idOf(new MessageImpl()));
        Message adminRequest = new MessageImpl();
        adminRequest.put(SecurityContext.class, new RolePrefixSecurityContextImpl(new Subject(), "ROLE_", "prefix"));
        assertNull(RequestTenant.idOf(adminRequest));
    }
}
