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
package org.apache.unomi.rest.exception;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility for sanitizing untrusted, request-derived values before they are written to logs.
 * <p>
 * Centralizes the defenses against log injection (control characters, log-format markers) and
 * unbounded log growth (length limits) that are shared by the REST exception mappers.
 */
final class LogSanitizer {

    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_QUERY_STRING_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_CLASS_NAME_LENGTH = 100;
    private static final int MAX_METHOD_LENGTH = 10;
    private static final int MAX_QUERY_PARAMS = 10;
    private static final int MAX_QUERY_PARAM_VALUE_LENGTH = 50;

    private static final Set<String> VALID_HTTP_METHODS = new HashSet<>(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT"));

    private LogSanitizer() {
    }

    /**
     * Replaces every character that is not printable ASCII (or is a log-format marker such as
     * {@code \ { } % $}) with an underscore. This removes newlines, tabs and other control
     * characters that could be used for log injection.
     */
    static String forLogging(String input) {
        if (input == null) {
            return "";
        }
        if (input.length() > MAX_MESSAGE_LENGTH) {
            input = input.substring(0, MAX_MESSAGE_LENGTH) + "...[truncated]";
        }
        StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 0x20 && c <= 0x7E && c != '\\' && c != '{' && c != '}' && c != '%' && c != '$') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    static String url(String url) {
        if (url == null) {
            return "null";
        }
        if (url.length() > MAX_URL_LENGTH) {
            url = url.substring(0, MAX_URL_LENGTH) + "...[truncated]";
        }
        return forLogging(url);
    }

    static String queryString(String queryString) {
        if (queryString == null) {
            return "";
        }
        if (queryString.length() > MAX_QUERY_STRING_LENGTH) {
            queryString = queryString.substring(0, MAX_QUERY_STRING_LENGTH) + "...[truncated]";
        }
        return forLogging(queryString);
    }

    static String httpMethod(String method) {
        if (method == null || method.isEmpty()) {
            return "UNKNOWN";
        }
        String sanitized = forLogging(method.toUpperCase());
        if (VALID_HTTP_METHODS.contains(sanitized)) {
            return sanitized;
        }
        if (sanitized.length() > MAX_METHOD_LENGTH) {
            return sanitized.substring(0, MAX_METHOD_LENGTH) + "...";
        }
        return sanitized;
    }

    static String className(String className) {
        if (className == null || className.isEmpty()) {
            return "Unknown";
        }
        StringBuilder sanitized = new StringBuilder(className.length());
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '$' || c == '_' || c == '.') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        String result = sanitized.toString();
        if (result.length() > MAX_CLASS_NAME_LENGTH) {
            return result.substring(0, MAX_CLASS_NAME_LENGTH) + "...";
        }
        return result;
    }

    static String queryParameters(Map<String, List<String>> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int paramCount = 0;
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            if (paramCount >= MAX_QUERY_PARAMS) {
                sb.append("...[more params]");
                break;
            }
            if (paramCount > 0) {
                sb.append("&");
            }
            sb.append(url(entry.getKey())).append("=");
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String value = url(entry.getValue().get(0));
                if (value.length() > MAX_QUERY_PARAM_VALUE_LENGTH) {
                    value = value.substring(0, MAX_QUERY_PARAM_VALUE_LENGTH) + "...";
                }
                sb.append(value);
            }
            paramCount++;
        }
        return sb.toString();
    }
}
