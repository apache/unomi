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
import org.junit.jupiter.api.Test;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the REST exception mappers (UNOMI-928, UNOMI-952). These verify the status code
 * and JSON body contract directly, without a running container, so the mapper logic is validated
 * deterministically regardless of how a given endpoint deserializes its request body.
 */
class RestExceptionMapperTest {

    @Test
    void jsonMappingException_mapsToBadRequest() {
        Response response = new JsonMappingExceptionMapper()
                .toResponse(JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "cannot deserialize"));
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void runtimeException_mapsToInternalServerError() {
        Response response = new RuntimeExceptionMapper().toResponse(new RuntimeException("boom"));
        assertErrorResponse(response, 500, "internalServerError");
    }

    @Test
    void runtimeException_withJsonMappingCause_mapsToBadRequest() {
        RuntimeException exception = new RuntimeException(
                JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "cannot deserialize"));
        Response response = new RuntimeExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void runtimeException_withJsonParseCause_mapsToBadRequest() {
        RuntimeException exception = new RuntimeException(new JsonParseException(null, "malformed"));
        Response response = new RuntimeExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void internalServerError_withJsonMappingCause_mapsToBadRequest() {
        InternalServerErrorException exception = new InternalServerErrorException("wrapped",
                JsonMappingException.from((com.fasterxml.jackson.core.JsonParser) null, "cannot deserialize"));
        Response response = new InternalServerErrorExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void internalServerError_withJsonParseCause_mapsToBadRequest() {
        InternalServerErrorException exception = new InternalServerErrorException("wrapped",
                new JsonParseException(null, "malformed"));
        Response response = new InternalServerErrorExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void internalServerError_withNonJsonCause_mapsToInternalServerError() {
        InternalServerErrorException exception = new InternalServerErrorException("kaboom",
                new IllegalStateException("server fault"));
        Response response = new InternalServerErrorExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 500, "internalServerError");
    }

    @Test
    void illegalArgumentException_mapsToBadRequest() {
        Response response = new IllegalArgumentExceptionMapper()
                .toResponse(new IllegalArgumentException("Invalid rule condition:\n- missing type"));
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void badSegmentConditionException_mapsToBadRequest() {
        Response response = new BadSegmentConditionExceptionMapper()
                .toResponse(new BadSegmentConditionException("Invalid segment condition:\n- missing parameter"));
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void runtimeException_withIllegalArgumentCause_mapsToBadRequest() {
        RuntimeException exception = new RuntimeException(new IllegalArgumentException("invalid rule"));
        Response response = new RuntimeExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void runtimeException_withBadSegmentConditionCause_mapsToBadRequest() {
        RuntimeException exception = new RuntimeException(new BadSegmentConditionException("invalid segment"));
        Response response = new RuntimeExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void internalServerError_withIllegalArgumentCause_mapsToBadRequest() {
        InternalServerErrorException exception = new InternalServerErrorException("wrapped",
                new IllegalArgumentException("invalid rule"));
        Response response = new InternalServerErrorExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    @Test
    void internalServerError_withBadSegmentConditionCause_mapsToBadRequest() {
        InternalServerErrorException exception = new InternalServerErrorException("wrapped",
                new BadSegmentConditionException("invalid segment"));
        Response response = new InternalServerErrorExceptionMapper().toResponse(exception);
        assertErrorResponse(response, 400, "badRequest");
    }

    private static void assertErrorResponse(Response response, int expectedStatus, String expectedErrorMessage) {
        assertEquals(expectedStatus, response.getStatus());
        Object entity = response.getEntity();
        assertTrue(entity instanceof Map, "Expected a Map entity but was: " + entity);
        assertEquals(expectedErrorMessage, ((Map<?, ?>) entity).get("errorMessage"));
    }
}
