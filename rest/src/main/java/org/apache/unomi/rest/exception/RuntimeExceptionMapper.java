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

import org.apache.commons.lang.ArrayUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import java.util.HashMap;

@Provider
@Component(service=ExceptionMapper.class)
public class RuntimeExceptionMapper implements ExceptionMapper<RuntimeException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeExceptionMapper.class.getName());

    @Override
    public Response toResponse(RuntimeException exception) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("errorMessage", "internalServerError");
        LOGGER.error(
                "Internal server error {}: {} in {} (Set RuntimeExceptionMapper in debug to get the full stacktrace)",
                exception.getMessage(),
                exception,
                ArrayUtils.isEmpty(exception.getStackTrace()) ? "Stack not available" : exception.getStackTrace()[0]
        );
        LOGGER.debug("{}", exception.getMessage(), exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).header("Content-Type", MediaType.APPLICATION_JSON).entity(body).build();
    }
}
