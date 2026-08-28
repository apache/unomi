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
package org.apache.unomi.services.actions.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.services.impl.TypeResolutionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionExecutorDispatcherImplTest {

    @Mock
    DefinitionsService definitionsService;

    @Mock
    ActionDispatcher actionDispatcher;

    @Mock
    Event event;

    @Test
    void shouldResolveActionTypeBeforeDispatchingToActionDispatcher() throws Exception {
        ActionExecutorDispatcherImpl dispatcher = new ActionExecutorDispatcherImpl();
        dispatcher.setDefinitionsService(definitionsService);

        putActionDispatcher(dispatcher, "test", actionDispatcher);

        ActionType actionType = new ActionType();
        actionType.setItemId("testActionType");
        actionType.setActionExecutor("test:doSomething");
        when(definitionsService.getActionType("testActionType")).thenReturn(actionType);
        when(definitionsService.getTypeResolutionService()).thenReturn(new TypeResolutionServiceImpl(definitionsService));
        when(actionDispatcher.execute(any(Action.class), eq(event), eq("doSomething"))).thenReturn(EventService.NO_CHANGE);

        // Simulate JSON-deserialized action: only actionTypeId is present
        Action action = new Action();
        action.setActionTypeId("testActionType");

        int result = dispatcher.execute(action, event);

        assertEquals(EventService.NO_CHANGE, result, "Dispatcher should return the result from ActionDispatcher when action type is resolved");
        assertNotNull(action.getActionType(), "Action type should be resolved from actionTypeId before dispatching");
        verify(actionDispatcher).execute(any(Action.class), eq(event), eq("doSomething"));
    }

    @Test
    void shouldReturnNoChangeWhenActionTypeCannotBeResolved() {
        ActionExecutorDispatcherImpl dispatcher = new ActionExecutorDispatcherImpl();
        dispatcher.setDefinitionsService(definitionsService);

        // Mock TypeResolutionService to be available but return null for missing type
        when(definitionsService.getTypeResolutionService()).thenReturn(new TypeResolutionServiceImpl(definitionsService));
        when(definitionsService.getActionType("missingType")).thenReturn(null);

        Action action = new Action();
        action.setActionTypeId("missingType");

        int result = dispatcher.execute(action, event);

        assertEquals(EventService.NO_CHANGE, result, "Dispatcher should return NO_CHANGE when actionTypeId cannot be resolved");
    }

    @SuppressWarnings("unchecked")
    private static void putActionDispatcher(ActionExecutorDispatcherImpl dispatcher, String prefix, ActionDispatcher actionDispatcher) throws Exception {
        Field field = ActionExecutorDispatcherImpl.class.getDeclaredField("actionDispatchers");
        field.setAccessible(true);
        Map<String, ActionDispatcher> map = (Map<String, ActionDispatcher>) field.get(dispatcher);
        map.put(prefix, actionDispatcher);
    }
}


