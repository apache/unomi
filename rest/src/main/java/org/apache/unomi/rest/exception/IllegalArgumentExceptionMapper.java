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

/**
 * Maps {@link IllegalArgumentException} (e.g. invalid rule condition on {@code POST /cxs/rules})
 * to a {@code 400 Bad Request} instead of a {@code 500 Internal Server Error}.
 */
@Provider
@Component(service = ExceptionMapper.class)
public class IllegalArgumentExceptionMapper extends AbstractRestExceptionMapper
        implements ExceptionMapper<IllegalArgumentException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class.getName());

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        String requestContext = buildRequestContext();
        String errorMessage = LogSanitizer.forLogging(messageOrType(exception));

        LOGGER.warn("Bad request on {} - Invalid argument: {} (Set IllegalArgumentExceptionMapper to debug to get the full stacktrace)",
                requestContext, errorMessage);
        LOGGER.debug("Full illegal argument exception details for request: {}", requestContext, exception);

        return badRequestResponse();
    }
}
