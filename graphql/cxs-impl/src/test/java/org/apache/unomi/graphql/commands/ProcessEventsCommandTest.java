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
package org.apache.unomi.graphql.commands;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessEventsCommandTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private EventService eventService;
    @Mock
    private ExecutionContextManager executionContextManager;
    @Mock
    private ExecutionContext executionContext;

    @Test
    void allowsEventWhenTenantGatePasses() {
        Event event = viewEvent();
        when(executionContextManager.getCurrentContext()).thenReturn(executionContext);
        when(executionContext.getTenantId()).thenReturn(TENANT_ID);
        when(eventService.isEventAllowedForTenant(event, TENANT_ID, null)).thenReturn(true);

        assertTrue(ProcessEventsCommand.isEventAllowedForCurrentTenant(event, eventService, executionContextManager));
        verify(eventService).isEventAllowedForTenant(event, TENANT_ID, null);
    }

    @Test
    void refusesEventWhenTenantGateFails() {
        Event event = viewEvent();
        when(executionContextManager.getCurrentContext()).thenReturn(executionContext);
        when(executionContext.getTenantId()).thenReturn(TENANT_ID);
        when(eventService.isEventAllowedForTenant(event, TENANT_ID, null)).thenReturn(false);

        assertFalse(ProcessEventsCommand.isEventAllowedForCurrentTenant(event, eventService, executionContextManager));
        verify(eventService).isEventAllowedForTenant(event, TENANT_ID, null);
    }

    @Test
    void passesNullTenantWhenThereIsNoExecutionContext() {
        Event event = viewEvent();
        when(eventService.isEventAllowedForTenant(event, null, null)).thenReturn(false);

        assertFalse(ProcessEventsCommand.isEventAllowedForCurrentTenant(event, eventService, null));
        verify(eventService).isEventAllowedForTenant(event, null, null);
    }

    private static Event viewEvent() {
        return new Event("view", null, new Profile(), null, null, null, new Date());
    }
}
