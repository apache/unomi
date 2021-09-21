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

package org.apache.unomi.services.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;

/**
 * This class is the base interface to define the action dispatcher to execute according to the action type of the action
 * The action executor dispatcher get the list of the action dispatchers present in unomi
 * When the execute method is called, the dispatch will be done according to the prefix of the action executor of the action type
 */
public interface ActionExecutorDispatcher {

    /**
     * Execute an action dispatcher according to the action type of the action
     *
     * @param action action to execute
     * @param event  received event
     * @return result code of the execution
     */
    int execute(Action action, Event event);
}
