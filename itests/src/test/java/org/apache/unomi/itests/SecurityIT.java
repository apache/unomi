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
package org.apache.unomi.itests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.security.auth.Subject;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SecurityIT extends BaseIT {

    private static final String SESSION_ID = "vuln-session-id";

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = CustomObjectMapper.getObjectMapper();
    }

    @Test
    public void testSystemOperationsAndContext() throws Exception {
        SecurityService securityService = getOsgiService(SecurityService.class);
        ExecutionContextManager contextManager = getOsgiService(ExecutionContextManager.class);

        // Test system subject creation and validation
        Subject systemSubject = securityService.getSystemSubject();
        assertNotNull("System subject should not be null", systemSubject);

        Set<String> roles = securityService.extractRolesFromSubject(systemSubject);
        assertTrue("System subject should have administrator role",
            roles.contains(UnomiRoles.ADMINISTRATOR));

        // Test system operation execution
        String result = contextManager.executeAsSystem(() -> {
            ExecutionContext ctx = contextManager.getCurrentContext();
            assertNotNull("System execution context should not be null", ctx);
            assertTrue("System context should have admin role",
                ctx.hasRole(UnomiRoles.ADMINISTRATOR));
            return "success";
        });
        assertEquals("System operation should execute successfully", "success", result);

        // Test context isolation
        ExecutionContext regularContext = contextManager.getCurrentContext();
        assertFalse("Regular context should not have admin role by default",
            regularContext.hasRole(UnomiRoles.ADMINISTRATOR));

        // Test error handling during system operation
        try {
            contextManager.executeAsSystem(() -> {
                throw new RuntimeException("Test exception");
            });
            fail("Should throw exception from system operation");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
            // Verify context is properly restored after exception
            ExecutionContext postErrorContext = contextManager.getCurrentContext();
            assertEquals("Context should be restored after error",
                regularContext.getTenantId(), postErrorContext.getTenantId());
        }
    }

    private TestUtils.RequestResponse executeContextJSONRequest(HttpPost request, String sessionId) throws IOException {
        return TestUtils.executeContextJSONRequest(request, sessionId);
    }

}
