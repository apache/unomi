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

import javax.servlet.http.HttpServletResponse;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Pre-parse guards for GraphQL request size. Call these before the document is parsed.
 */
public final class GraphQLRequestLimits {

    private GraphQLRequestLimits() {
    }

    /**
     * @return {@code true} when the query may be processed; {@code false} when a 413 was already sent
     */
    public static boolean checkQueryLength(String query, GraphQLLimitsConfig limits, HttpServletResponse res)
            throws IOException {
        if (limits == null) {
            return true;
        }
        int max = limits.getMaxQueryLengthChars();
        if (max <= 0 || query == null || query.length() <= max) {
            return true;
        }
        res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "GraphQL query exceeds the configured size limit");
        return false;
    }

    /**
     * Early refusal of a declared {@code Content-Length}. A body without one (chunked, or over 2 GB) is
     * capped while it is read, see {@link #boundedBody}.
     *
     * @return {@code true} when the body may be read; {@code false} when a 413 was already sent
     */
    public static boolean checkContentLength(int contentLength, GraphQLLimitsConfig limits, HttpServletResponse res)
            throws IOException {
        if (limits == null) {
            return true;
        }
        int max = limits.getMaxRequestBodyBytes();
        if (max <= 0 || contentLength < 0 || contentLength <= max) {
            return true;
        }
        res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "GraphQL request body exceeds the configured size limit");
        return false;
    }

    /**
     * Wraps the request body so that reading more than the configured number of bytes fails with
     * {@link BodyTooLargeException}, whatever the request declared.
     */
    public static InputStream boundedBody(InputStream body, GraphQLLimitsConfig limits) {
        if (limits == null || limits.getMaxRequestBodyBytes() <= 0) {
            return body;
        }
        return new BoundedInputStream(body, limits.getMaxRequestBodyBytes());
    }

    /** Sends the 413 for a body found too large while reading it. */
    public static void sendBodyTooLarge(HttpServletResponse res) throws IOException {
        res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "GraphQL request body exceeds the configured size limit");
    }

    /** The request body went over the configured limit while it was being read. */
    public static final class BodyTooLargeException extends IOException {
        BodyTooLargeException(long max) {
            super("GraphQL request body exceeds " + max + " bytes");
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long max;
        private long count;

        BoundedInputStream(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count(skipped);
            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        private void count(long n) throws BodyTooLargeException {
            count += n;
            if (count > max) {
                throw new BodyTooLargeException(max);
            }
        }
    }
}
