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

import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

@Provider
@Component(service = ExceptionMapper.class)
public class InternalServerErrorExceptionMapper implements ExceptionMapper<javax.ws.rs.InternalServerErrorException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalServerErrorExceptionMapper.class.getName());

    @Override
    public Response toResponse(javax.ws.rs.InternalServerErrorException exception) {
        String requestContext = buildRequestContext();
        Throwable rootCause = getRootCause(exception);
        
        // Check if this is actually a client error (JSON deserialization) that was wrapped
        boolean isClientError = rootCause != null && 
            (rootCause instanceof com.fasterxml.jackson.databind.JsonMappingException ||
             rootCause instanceof com.fasterxml.jackson.core.JsonParseException);
        
        if (isClientError) {
            // Return 400 Bad Request for client errors
            HashMap<String, Object> body = new HashMap<>();
            body.put("errorMessage", "badRequest");
            
            String errorMessage = sanitizeForLogging(extractJsonErrorMessage(rootCause));
            
            LOGGER.warn("Bad request on {} - JSON deserialization error: {} (Set InternalServerErrorExceptionMapper to debug to get the full stacktrace)", 
                    requestContext, errorMessage);
            LOGGER.debug("Full JSON mapping exception details for request: {}", requestContext, exception);
            
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        }
        
        // True server error - return 500
        HashMap<String, Object> body = new HashMap<>();
        body.put("errorMessage", "internalServerError");
        
        // Build detailed error message
        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("Request failed: ").append(requestContext);
        
        if (rootCause != null && rootCause != exception) {
            String rootCauseClassName = sanitizeClassName(rootCause.getClass().getSimpleName());
            errorDetails.append(" - Root cause: ").append(rootCauseClassName);
            String rootCauseMessage = rootCause.getMessage();
            if (rootCauseMessage != null && !rootCauseMessage.isEmpty()) {
                errorDetails.append(" (").append(sanitizeForLogging(rootCauseMessage)).append(")");
            }
        }
        
        String exceptionMessage = exception.getMessage();
        if (exceptionMessage != null && !exceptionMessage.isEmpty() && 
            (rootCause == null || !exceptionMessage.equals(rootCause.getMessage()))) {
            errorDetails.append(" - Error: ").append(sanitizeForLogging(exceptionMessage));
        }
        
        LOGGER.error("{} (Set InternalServerErrorExceptionMapper to debug to get the full stacktrace)", 
                errorDetails.toString(), exception);
        LOGGER.debug("Full exception details for request: {}", requestContext, exception);
        
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private String extractJsonErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown JSON error";
        }
        
        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return throwable.getClass().getSimpleName();
    }

    private String buildRequestContext() {
        StringBuilder context = new StringBuilder();
        
        try {
            Message message = JAXRSUtils.getCurrentMessage();
            if (message != null) {
                HttpServletRequest request = (HttpServletRequest) message.get("HTTP.REQUEST");
                if (request != null) {
                    String method = sanitizeHttpMethod(request.getMethod());
                    String requestURI = sanitizeUrl(request.getRequestURI());
                    context.append(method).append(" ").append(requestURI);
                    
                    String queryString = request.getQueryString();
                    if (queryString != null && !queryString.isEmpty()) {
                        context.append("?").append(sanitizeQueryString(queryString));
                    }
                } else {
                    // Try to get from CXF message properties
                    String httpMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
                    String basePath = (String) message.get(Message.BASE_PATH);
                    String pathInfo = (String) message.get(Message.PATH_INFO);
                    String requestURI = (String) message.get(Message.REQUEST_URI);
                    
                    if (requestURI != null) {
                        context.append(httpMethod != null ? sanitizeHttpMethod(httpMethod) : "UNKNOWN")
                               .append(" ")
                               .append(sanitizeUrl(requestURI));
                    } else if (basePath != null || pathInfo != null) {
                        String path = (basePath != null ? basePath : "") + (pathInfo != null ? pathInfo : "");
                        context.append(httpMethod != null ? sanitizeHttpMethod(httpMethod) : "UNKNOWN")
                               .append(" ")
                               .append(sanitizeUrl(path));
                    } else {
                        UriInfo uriInfo = message.get(UriInfo.class);
                        if (uriInfo != null) {
                            String path = sanitizeUrl(uriInfo.getPath());
                            context.append("HTTP ").append(path);
                            
                            if (uriInfo.getQueryParameters() != null && !uriInfo.getQueryParameters().isEmpty()) {
                                context.append("?").append(sanitizeQueryParameters(uriInfo.getQueryParameters()));
                            }
                        } else {
                            context.append("UNKNOWN REQUEST");
                        }
                    }
                }
            } else {
                context.append("REQUEST CONTEXT UNAVAILABLE");
            }
        } catch (Exception e) {
            LOGGER.debug("Error building request context", e);
            context.append("REQUEST CONTEXT UNAVAILABLE");
        }
        
        return context.toString();
    }

    private String sanitizeUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Limit URL length to prevent log injection and excessive logging
        if (url.length() > 500) {
            url = url.substring(0, 500) + "...[truncated]";
        }
        // Remove ALL control characters (0x00-0x1F, 0x7F-0x9F) and other dangerous characters
        // Only allow printable ASCII characters and safe URL characters
        return sanitizeForLogging(url);
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null) {
            return "";
        }
        // Limit query string length
        if (queryString.length() > 200) {
            queryString = queryString.substring(0, 200) + "...[truncated]";
        }
        // Remove ALL control characters and dangerous characters
        return sanitizeForLogging(queryString);
    }

    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Allow printable ASCII characters (0x20-0x7E) except potentially dangerous ones
            // Also allow some safe non-ASCII characters for international URLs (but be conservative)
            if (c >= 0x20 && c <= 0x7E) {
                // Remove characters that could be used for injection: newlines, carriage returns, tabs, backslashes
                // Also remove characters that could break log format: { } % $ 
                if (c != '\n' && c != '\r' && c != '\t' && c != '\\' && c != '{' && c != '}' && c != '%' && c != '$') {
                    sanitized.append(c);
                } else {
                    sanitized.append('_');
                }
            } else if (c == '\n' || c == '\r' || c == '\t') {
                // Explicitly handle common control characters
                sanitized.append('_');
            } else if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                // Remove all other control characters (0x00-0x1F, 0x7F-0x9F)
                sanitized.append('_');
            } else {
                // For other characters (like unicode), replace with underscore to be safe
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    private String sanitizeQueryParameters(Map<String, java.util.List<String>> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int paramCount = 0;
        for (Map.Entry<String, java.util.List<String>> entry : queryParams.entrySet()) {
            if (paramCount > 0) {
                sb.append("&");
            }
            if (paramCount >= 10) {
                sb.append("...[more params]");
                break;
            }
            String key = sanitizeUrl(entry.getKey());
            sb.append(key).append("=");
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String value = sanitizeUrl(entry.getValue().get(0));
                if (value.length() > 50) {
                    value = value.substring(0, 50) + "...";
                }
                sb.append(value);
            }
            paramCount++;
        }
        return sb.toString();
    }

    private String sanitizeHttpMethod(String method) {
        if (method == null || method.isEmpty()) {
            return "UNKNOWN";
        }
        // HTTP methods should only contain uppercase letters, but sanitize to be safe
        String sanitized = sanitizeForLogging(method.toUpperCase());
        // Whitelist valid HTTP methods
        if (sanitized.equals("GET") || sanitized.equals("POST") || sanitized.equals("PUT") || 
            sanitized.equals("DELETE") || sanitized.equals("PATCH") || sanitized.equals("HEAD") || 
            sanitized.equals("OPTIONS") || sanitized.equals("TRACE") || sanitized.equals("CONNECT")) {
            return sanitized;
        }
        // If not a standard method, still return sanitized but truncated
        if (sanitized.length() > 10) {
            return sanitized.substring(0, 10) + "...";
        }
        return sanitized;
    }

    private String sanitizeClassName(String className) {
        if (className == null || className.isEmpty()) {
            return "Unknown";
        }
        // Class names should only contain alphanumeric, $, and _ characters
        // Remove anything else to prevent injection
        StringBuilder sanitized = new StringBuilder(className.length());
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || 
                (c >= '0' && c <= '9') || c == '$' || c == '_' || c == '.') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        // Limit length
        String result = sanitized.toString();
        if (result.length() > 100) {
            return result.substring(0, 100) + "...";
        }
        return result;
    }

    private Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return throwable;
        }
        return getRootCause(cause);
    }
}
