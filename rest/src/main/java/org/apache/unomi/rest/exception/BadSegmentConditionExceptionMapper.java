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

import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps {@link BadSegmentConditionException} (invalid segment condition on {@code POST /cxs/segments})
 * to a {@code 400 Bad Request} instead of a {@code 500 Internal Server Error}.
 */
@Provider
@Component(service = ExceptionMapper.class)
public class BadSegmentConditionExceptionMapper extends AbstractRestExceptionMapper
        implements ExceptionMapper<BadSegmentConditionException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadSegmentConditionExceptionMapper.class.getName());

    @Override
    public Response toResponse(BadSegmentConditionException exception) {
        String requestContext = buildRequestContext();
        String errorMessage = LogSanitizer.forLogging(messageOrType(exception));

        LOGGER.warn("Bad request on {} - Invalid segment condition: {} (Set BadSegmentConditionExceptionMapper to debug to get the full stacktrace)",
                requestContext, errorMessage);
        LOGGER.debug("Full bad segment condition exception details for request: {}", requestContext, exception);

        return badRequestResponse();
    }
}
