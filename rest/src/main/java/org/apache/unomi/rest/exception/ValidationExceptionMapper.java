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

import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Component(service = ExceptionMapper.class)
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    private static final Logger logger = LoggerFactory.getLogger(ValidationExceptionMapper.class.getName());

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        exception.getConstraintViolations().forEach(constraintViolation -> {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("value %s from %s %s", constraintViolation.getInvalidValue(),
                        constraintViolation.getPropertyPath().toString(), constraintViolation.getMessage()), exception);
            }
            logger.error(constraintViolation.getPropertyPath().toString() + " " + constraintViolation.getMessage() + ". Enable debug log "
                    + "level for more informations about the invalid value received");
        });

        return Response.status(Response.Status.BAD_REQUEST).header("Content-Type", MediaType.TEXT_PLAIN)
                .entity("Request rejected by the server because: Invalid received data").build();
    }

}
