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
package org.apache.unomi.graphql.limits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphQLRequestLimitsTest {

    @Mock
    private GraphQLLimitsConfig limits;
    @Mock
    private HttpServletResponse response;

    @Test
    void checkQueryLength_allowsWhenLimitsMissing() throws IOException {
        assertTrue(GraphQLRequestLimits.checkQueryLength("query { x }", null, response));
        verify(response, never()).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL query exceeds the configured size limit");
    }

    @Test
    void checkQueryLength_rejectsWhenOverLimit() throws IOException {
        when(limits.getMaxQueryLengthChars()).thenReturn(8);

        assertFalse(GraphQLRequestLimits.checkQueryLength("0123456789", limits, response));
        verify(response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL query exceeds the configured size limit");
    }

    @Test
    void checkQueryLength_allowsWhenAtLimit() throws IOException {
        when(limits.getMaxQueryLengthChars()).thenReturn(5);

        assertTrue(GraphQLRequestLimits.checkQueryLength("12345", limits, response));
        verify(response, never()).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL query exceeds the configured size limit");
    }

    @Test
    void checkQueryLength_disabledWhenZero() throws IOException {
        when(limits.getMaxQueryLengthChars()).thenReturn(0);

        assertTrue(GraphQLRequestLimits.checkQueryLength("anything-long", limits, response));
        verify(response, never()).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL query exceeds the configured size limit");
    }

    @Test
    void checkContentLength_rejectsWhenOverLimit() throws IOException {
        when(limits.getMaxRequestBodyBytes()).thenReturn(100);

        assertFalse(GraphQLRequestLimits.checkContentLength(101, limits, response));
        verify(response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL request body exceeds the configured size limit");
    }

    @Test
    void checkContentLength_skipsWhenContentLengthUnknown() throws IOException {
        when(limits.getMaxRequestBodyBytes()).thenReturn(100);

        assertTrue(GraphQLRequestLimits.checkContentLength(-1, limits, response));
        verify(response, never()).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL request body exceeds the configured size limit");
    }
}
