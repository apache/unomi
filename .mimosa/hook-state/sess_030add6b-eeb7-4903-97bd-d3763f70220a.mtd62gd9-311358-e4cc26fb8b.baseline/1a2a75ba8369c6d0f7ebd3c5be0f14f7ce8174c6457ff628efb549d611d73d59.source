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
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.apache.unomi.tracing.api.RequestTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestActionExecutorDispatcher implements ActionExecutorDispatcher {
    private final DefinitionsService definitionsService;
    private final PersistenceService persistenceService;
    private int defaultReturnValue = EventService.NO_CHANGE;
    private Map<String, ActionExecutor> executors = new HashMap<>();
    private RequestTracer tracer;

    private static final Logger LOGGER = LoggerFactory.getLogger(TestActionExecutorDispatcher.class.getName());

    public TestActionExecutorDispatcher(DefinitionsService definitionsService, PersistenceService persistenceService) {
        this.definitionsService = definitionsService;
        this.persistenceService = persistenceService;
    }

    public void setTracer(RequestTracer tracer) {
        this.tracer = tracer;
    }

    public void setDefaultReturnValue(int defaultReturnValue) {
        this.defaultReturnValue = defaultReturnValue;
    }

    public void addExecutor(String actionId, ActionExecutor executor) {
        executors.put(actionId, executor);
    }

    @Override
    public int execute(Action action, Event event) {
        if (action == null || action.getActionType() == null) {
            if (tracer != null && tracer.isEnabled()) {
                tracer.trace("Action or action type is null, returning default value: " + defaultReturnValue, action);
            }
            LOGGER.warn("Action or action type is null");
            return defaultReturnValue;
        }

        String actionId = action.getActionType().getActionExecutor();
        if (tracer != null && tracer.isEnabled()) {
            tracer.startOperation("action-execution", "Executing action: " + actionId, action);
        }

        ActionExecutor executor = executors.get(actionId);
        if (executor != null) {
            int result = executor.execute(action, event);
            if (tracer != null && tracer.isEnabled()) {
                tracer.endOperation(result != EventService.NO_CHANGE,
                    "Action execution completed with result: " + result);
            }
            return result;
        } else {
            LOGGER.warn("Missing action executor for actionTypeId={}", actionId);
        }

        if (tracer != null && tracer.isEnabled()) {
            tracer.endOperation(false,
                "No executor found for action: " + actionId + ", returning default value: " + defaultReturnValue);
        }
        return defaultReturnValue;
    }
}
