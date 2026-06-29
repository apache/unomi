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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for the REST {@code ExceptionMapper}s, factoring out the behaviour they share:
 * building a sanitized request context for logging, walking to a root cause, detecting JSON
 * deserialization failures, and producing the standard JSON error responses.
 */
public abstract class AbstractRestExceptionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRestExceptionMapper.class.getName());

    private static final String ERROR_MESSAGE_KEY = "errorMessage";

    /**
     * @return a {@code 400 Bad Request} JSON response: {@code {"errorMessage":"badRequest"}}
     */
    protected Response badRequestResponse() {
        return jsonErrorResponse(Response.Status.BAD_REQUEST, "badRequest");
    }

    /**
     * @return a {@code 500 Internal Server Error} JSON response: {@code {"errorMessage":"internalServerError"}}
     */
    protected Response internalServerErrorResponse() {
        return jsonErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "internalServerError");
    }

    private Response jsonErrorResponse(Response.Status status, String errorMessage) {
        Map<String, Object> body = new HashMap<>();
        body.put(ERROR_MESSAGE_KEY, errorMessage);
        return Response.status(status).header("Content-Type", MediaType.APPLICATION_JSON).entity(body).build();
    }

    /**
     * @return {@code true} when the given root cause is a Jackson deserialization failure, i.e. a
     * client error (malformed/mistyped request body) rather than a genuine server fault.
     */
    protected boolean isJsonDeserializationError(Throwable rootCause) {
        return rootCause instanceof JsonMappingException || rootCause instanceof JsonParseException;
    }

    /**
     * @return {@code true} when the throwable represents invalid client input rejected by domain
     * validation (e.g. rule or segment condition checks), rather than a server fault.
     */
    protected boolean isClientValidationError(Throwable throwable) {
        return throwable instanceof IllegalArgumentException || throwable instanceof BadSegmentConditionException;
    }

    protected Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        // Standard Java Throwable.initCause() prevents circular cause chains, so the visited-set
        // cycle guard below is defensive and not reachable in practice.
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current.getCause() != null && current.getCause() != current && visited.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * @return the throwable's message, or its simple class name when no message is available.
     */
    protected String messageOrType(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        return (message != null && !message.isEmpty()) ? message : throwable.getClass().getSimpleName();
    }

    /**
     * Builds a sanitized "METHOD /path?query" description of the current request for logging.
     * Never throws: returns a placeholder when the request context cannot be resolved.
     */
    protected String buildRequestContext() {
        StringBuilder context = new StringBuilder();
        try {
            Message message = JAXRSUtils.getCurrentMessage();
            if (message == null) {
                return "REQUEST CONTEXT UNAVAILABLE";
            }
            HttpServletRequest request = (HttpServletRequest) message.get("HTTP.REQUEST");
            if (request != null) {
                appendFromServletRequest(context, request);
            } else {
                appendFromCxfMessage(context, message);
            }
        } catch (Exception e) {
            LOGGER.debug("Error building request context", e);
            return "REQUEST CONTEXT UNAVAILABLE";
        }
        return context.toString();
    }

    private void appendFromServletRequest(StringBuilder context, HttpServletRequest request) {
        context.append(LogSanitizer.httpMethod(request.getMethod()))
                .append(" ")
                .append(LogSanitizer.url(request.getRequestURI()));
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            context.append("?").append(LogSanitizer.queryString(queryString));
        }
    }

    private void appendFromCxfMessage(StringBuilder context, Message message) {
        String httpMethod = (String) message.get(Message.HTTP_REQUEST_METHOD);
        String basePath = (String) message.get(Message.BASE_PATH);
        String pathInfo = (String) message.get(Message.PATH_INFO);
        String requestURI = (String) message.get(Message.REQUEST_URI);

        if (requestURI != null) {
            context.append(sanitizedMethodOrUnknown(httpMethod)).append(" ").append(LogSanitizer.url(requestURI));
        } else if (basePath != null || pathInfo != null) {
            String path = (basePath != null ? basePath : "") + (pathInfo != null ? pathInfo : "");
            context.append(sanitizedMethodOrUnknown(httpMethod)).append(" ").append(LogSanitizer.url(path));
        } else {
            UriInfo uriInfo = message.get(UriInfo.class);
            if (uriInfo != null) {
                context.append("HTTP ").append(LogSanitizer.url(uriInfo.getPath()));
                if (uriInfo.getQueryParameters() != null && !uriInfo.getQueryParameters().isEmpty()) {
                    context.append("?").append(LogSanitizer.queryParameters(uriInfo.getQueryParameters()));
                }
            } else {
                context.append("UNKNOWN REQUEST");
            }
        }
    }

    private String sanitizedMethodOrUnknown(String httpMethod) {
        return httpMethod != null ? LogSanitizer.httpMethod(httpMethod) : "UNKNOWN";
    }
}
