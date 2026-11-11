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
import java.io.IOException;

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
}
