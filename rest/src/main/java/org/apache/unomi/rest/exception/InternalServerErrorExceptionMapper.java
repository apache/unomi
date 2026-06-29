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

import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps {@link InternalServerErrorException}. When the underlying cause is a Jackson deserialization
 * failure or a domain validation error (a wrapped client error) the response is downgraded to a
 * {@code 400 Bad Request}; otherwise it remains a {@code 500 Internal Server Error} with detailed,
 * sanitized logging.
 */
@Provider
@Component(service = ExceptionMapper.class)
public class InternalServerErrorExceptionMapper extends AbstractRestExceptionMapper
        implements ExceptionMapper<InternalServerErrorException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalServerErrorExceptionMapper.class.getName());

    @Override
    public Response toResponse(InternalServerErrorException exception) {
        String requestContext = buildRequestContext();
        Throwable rootCause = getRootCause(exception);

        // A wrapped JSON deserialization failure or domain validation error is really a client
        // error -> 400 Bad Request.
        if (isJsonDeserializationError(rootCause) || isClientValidationError(rootCause)) {
            String errorMessage = LogSanitizer.forLogging(messageOrType(rootCause));
            LOGGER.warn("Bad request on {} - Root cause: {} (Set InternalServerErrorExceptionMapper to debug to get the full stacktrace)",
                    requestContext, errorMessage);
            LOGGER.debug("Full exception details for request: {}", requestContext, exception);
            return badRequestResponse();
        }

        // Genuine server error -> 500 with detailed context.
        LOGGER.error("{} (Set InternalServerErrorExceptionMapper to debug to get the full stacktrace)",
                buildServerErrorDetails(requestContext, exception, rootCause));
        LOGGER.debug("Full exception details for request: {}", requestContext, exception);

        return internalServerErrorResponse();
    }

    private String buildServerErrorDetails(String requestContext, InternalServerErrorException exception, Throwable rootCause) {
        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("Request failed: ").append(requestContext);

        if (rootCause != null && rootCause != exception) {
            errorDetails.append(" - Root cause: ").append(LogSanitizer.className(rootCause.getClass().getSimpleName()));
            String rootCauseMessage = rootCause.getMessage();
            if (rootCauseMessage != null && !rootCauseMessage.isEmpty()) {
                errorDetails.append(" (").append(LogSanitizer.forLogging(rootCauseMessage)).append(")");
            }
        }

        String exceptionMessage = exception.getMessage();
        if (exceptionMessage != null && !exceptionMessage.isEmpty()
                && (rootCause == null || !exceptionMessage.equals(rootCause.getMessage()))) {
            errorDetails.append(" - Error: ").append(LogSanitizer.forLogging(exceptionMessage));
        }
        return errorDetails.toString();
    }
}
