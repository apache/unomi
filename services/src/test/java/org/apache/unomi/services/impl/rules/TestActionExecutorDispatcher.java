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
package org.apache.unomi.services.impl.rules;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestActionExecutorDispatcher implements ActionExecutorDispatcher {
    private int defaultReturnValue = 0;
    private final Map<String, ActionExecutor> actionExecutors = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(TestActionExecutorDispatcher.class.getName());

    public TestActionExecutorDispatcher(DefinitionsService definitionsService, PersistenceService persistenceService) {
        // Register the SetEventOccurrenceCountAction
        TestSetEventOccurrenceCountAction eventOccurrenceAction = new TestSetEventOccurrenceCountAction(definitionsService, persistenceService);
        actionExecutors.put("setEventOccurenceCountAction", eventOccurrenceAction);
    }

    public void setDefaultReturnValue(int value) {
        this.defaultReturnValue = value;
    }

    public void setActionExecutor(String actionId, ActionExecutor executor) {
        actionExecutors.put(actionId, executor);
    }

    @Override
    public int execute(Action action, Event event) {
        if (action == null || action.getActionTypeId() == null) {
            return defaultReturnValue;
        }

        ActionExecutor executor = actionExecutors.get(action.getActionTypeId());
        if (executor != null) {
            return executor.execute(action, event);
        } else {
            LOGGER.warn("Missing action executor for actionTypeId={}", action.getActionTypeId());
        }

        return defaultReturnValue;
    }
}
