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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /** No declared length to refuse early; {@link GraphQLRequestLimits#boundedBody} caps such a body. */
    @Test
    void checkContentLength_skipsWhenContentLengthUnknown() throws IOException {
        when(limits.getMaxRequestBodyBytes()).thenReturn(100);

        assertTrue(GraphQLRequestLimits.checkContentLength(-1, limits, response));
        verify(response, never()).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "GraphQL request body exceeds the configured size limit");
    }

    /** A body within the limit reads through unchanged. */
    @Test
    void boundedBody_readsBodyWithinLimit() throws IOException {
        when(limits.getMaxRequestBodyBytes()).thenReturn(10);
        byte[] body = "0123456789".getBytes();

        InputStream in = GraphQLRequestLimits.boundedBody(new ByteArrayInputStream(body), limits);

        assertArrayEquals(body, in.readAllBytes());
    }

    /** A body without Content-Length (chunked) is still capped while it is read. */
    @Test
    void boundedBody_failsOnceOverLimit() {
        when(limits.getMaxRequestBodyBytes()).thenReturn(10);
        InputStream in = GraphQLRequestLimits.boundedBody(new ByteArrayInputStream(new byte[11]), limits);

        assertThrows(GraphQLRequestLimits.BodyTooLargeException.class, in::readAllBytes);
    }

    /** Single-byte reads are counted too. */
    @Test
    void boundedBody_countsSingleByteReads() throws IOException {
        when(limits.getMaxRequestBodyBytes()).thenReturn(2);
        InputStream in = GraphQLRequestLimits.boundedBody(new ByteArrayInputStream(new byte[3]), limits);

        in.read();
        in.read();
        assertThrows(GraphQLRequestLimits.BodyTooLargeException.class, in::read);
    }

    @Test
    void boundedBody_unwrappedWhenDisabledOrMissing() {
        InputStream raw = new ByteArrayInputStream(new byte[0]);
        assertSame(raw, GraphQLRequestLimits.boundedBody(raw, null));

        when(limits.getMaxRequestBodyBytes()).thenReturn(0);
        assertSame(raw, GraphQLRequestLimits.boundedBody(raw, limits));
    }
}
