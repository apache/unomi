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

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Component(service = ExceptionMapper.class)
public class RuntimeExceptionMapper extends AbstractRestExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExceptionMapper.class.getName());

    @Override
    public Response toResponse(RuntimeException exception) {
        String requestContext = buildRequestContext();
        Throwable rootCause = getRootCause(exception);
        String rootCauseClassName = LogSanitizer.className(rootCause != null ? rootCause.getClass().getSimpleName() : "Unknown");
        String rootCauseMessage = LogSanitizer.forLogging(rootCause != null && rootCause.getMessage() != null
                ? rootCause.getMessage()
                : (exception.getMessage() != null ? exception.getMessage() : ""));

        // For client errors (like deserialization), log at WARN level. For true server errors, log at ERROR level.
        if (isJsonDeserializationError(rootCause)) {
            LOGGER.warn(
                    "Bad request on {} - Root cause: {} - {} (Set RuntimeExceptionMapper to debug to get the full stacktrace)",
                    requestContext, rootCauseClassName, rootCauseMessage);
        } else {
            LOGGER.error(
                    "Internal server error on {} - Root cause: {} - {} (Set RuntimeExceptionMapper to debug to get the full stacktrace)",
                    requestContext, rootCauseClassName, rootCauseMessage);
        }
        LOGGER.debug("Full exception details for request: {}", requestContext, exception);

        return internalServerErrorResponse();
    }
}
