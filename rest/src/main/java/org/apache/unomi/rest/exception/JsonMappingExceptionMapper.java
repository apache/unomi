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
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps Jackson {@link JsonMappingException} (raised when a syntactically valid JSON body cannot be
 * deserialized into the target type) to a {@code 400 Bad Request}, so a client mistake is not
 * reported as a server error.
 */
@Provider
@Component(service = ExceptionMapper.class)
public class JsonMappingExceptionMapper extends AbstractRestExceptionMapper implements ExceptionMapper<JsonMappingException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMappingExceptionMapper.class.getName());

    @Override
    public Response toResponse(JsonMappingException exception) {
        String requestContext = buildRequestContext();
        String errorMessage = LogSanitizer.forLogging(messageOrType(exception));

        // Client error: log at WARN level, full stack trace only at debug level.
        LOGGER.warn("Bad request on {} - JSON deserialization error: {} (Set JsonMappingExceptionMapper to debug to get the full stacktrace)",
                requestContext, errorMessage);
        LOGGER.debug("Full JSON mapping exception details for request: {}", requestContext, exception);

        return badRequestResponse();
    }
}
