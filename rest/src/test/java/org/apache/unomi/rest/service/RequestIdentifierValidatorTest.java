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
package org.apache.unomi.rest.service;

import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.schema.api.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdentifierValidatorTest {

    @Mock
    private SchemaService schemaService;

    @Test
    void requireValidSessionId_skipsNull() {
        assertDoesNotThrow(() -> RequestIdentifierValidator.requireValidSessionId(schemaService, null));
        verifyNoInteractions(schemaService);
    }

    @Test
    void requireValidSessionId_acceptsSchemaMatch() {
        when(schemaService.isValid(anyString(), eq(RequestIdentifierValidator.REQUEST_IDS_SCHEMA))).thenReturn(true);
        assertDoesNotThrow(() -> RequestIdentifierValidator.requireValidSessionId(schemaService, "dummy-session-id"));
    }

    @Test
    void requireValidSessionId_rejectsSchemaMismatch() {
        when(schemaService.isValid(anyString(), eq(RequestIdentifierValidator.REQUEST_IDS_SCHEMA))).thenReturn(false);
        assertThrows(InvalidRequestException.class,
                () -> RequestIdentifierValidator.requireValidSessionId(schemaService, "<script>alert();</script>"));
    }
}
