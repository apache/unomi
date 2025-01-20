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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;

import java.util.HashMap;
import java.util.Map;

/**
 * Test implementation of ActionExecutorDispatcher for unit tests.
 */
public class TestActionExecutorDispatcher implements ActionExecutorDispatcher {
    private final Map<String, ActionExecutor> actionExecutors = new HashMap<>();
    private int defaultReturnValue = EventService.NO_CHANGE;

    public void setActionExecutor(String actionId, ActionExecutor executor) {
        actionExecutors.put(actionId, executor);
    }

    public void setDefaultReturnValue(int value) {
        this.defaultReturnValue = value;
    }

    @Override
    public int execute(Action action, Event event) {
        if (action == null || action.getActionType() == null) {
            return defaultReturnValue;
        }

        String actionId = action.getActionType().getItemId();
        ActionExecutor executor = actionExecutors.get(actionId);
        if (executor != null) {
            return executor.execute(action, event);
        }

        return defaultReturnValue;
    }
} 