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

import com.fasterxml.jackson.databind.JsonMappingException;
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
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMappingExceptionMapper.class.getName());

    @Override
    public Response toResponse(JsonMappingException exception) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("errorMessage", "badRequest");
        
        String requestContext = buildRequestContext();
        String errorMessage = sanitizeForLogging(extractErrorMessage(exception));
        
        // Log at WARN level for client errors (not ERROR), and only include stack trace in debug
        LOGGER.warn("Bad request on {} - JSON deserialization error: {} (Set JsonMappingExceptionMapper to debug to get the full stacktrace)", 
                requestContext, errorMessage);
        LOGGER.debug("Full JSON mapping exception details for request: {}", requestContext, exception);
        
        return Response.status(Response.Status.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private String extractErrorMessage(JsonMappingException exception) {
        if (exception == null) {
            return "Unknown JSON mapping error";
        }
        
        String message = exception.getMessage();
        if (message != null && !message.isEmpty()) {
            // Extract the meaningful part of the error message
            if (message.contains("Unrecognized field")) {
                return message;
            }
            if (message.contains("Cannot deserialize")) {
                return message;
            }
            return message;
        }
        return exception.getClass().getSimpleName();
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
        if (url.length() > 500) {
            url = url.substring(0, 500) + "...[truncated]";
        }
        return sanitizeForLogging(url);
    }

    private String sanitizeQueryString(String queryString) {
        if (queryString == null) {
            return "";
        }
        if (queryString.length() > 200) {
            queryString = queryString.substring(0, 200) + "...[truncated]";
        }
        return sanitizeForLogging(queryString);
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

    private String sanitizeForLogging(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 0x20 && c <= 0x7E) {
                if (c != '\n' && c != '\r' && c != '\t' && c != '\\' && c != '{' && c != '}' && c != '%' && c != '$') {
                    sanitized.append(c);
                } else {
                    sanitized.append('_');
                }
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sanitized.append('_');
            } else if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                sanitized.append('_');
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    private String sanitizeHttpMethod(String method) {
        if (method == null || method.isEmpty()) {
            return "UNKNOWN";
        }
        String sanitized = sanitizeForLogging(method.toUpperCase());
        if (sanitized.equals("GET") || sanitized.equals("POST") || sanitized.equals("PUT") || 
            sanitized.equals("DELETE") || sanitized.equals("PATCH") || sanitized.equals("HEAD") || 
            sanitized.equals("OPTIONS") || sanitized.equals("TRACE") || sanitized.equals("CONNECT")) {
            return sanitized;
        }
        if (sanitized.length() > 10) {
            return sanitized.substring(0, 10) + "...";
        }
        return sanitized;
    }
}
