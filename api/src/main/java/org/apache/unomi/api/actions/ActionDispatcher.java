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
package org.apache.unomi.api.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventService;

/**
 * This class is the base interface for action dispatcher, which provide a pluggeable way to implement classes that
 * will dispatcher to execute actions. This may include dispatcher for scripting technologies such as Groovy or others.
 * The ActionExecutor class is not related to this, it is only used for Java implementations of Actions, whereas
 * ActionDispatchers may be used for other languages.
 */
public interface ActionDispatcher {

    /**
     * Retrieves the prefix that this dispatcher recognizes and that is used in the actionTypeId. For example to dispatch
     * to a GroovyActionDispatcher, the prefix could be : "groovy". Then when you want to refer to a Groovy action type
     * you could do something like this: "groovy:myGroovyAction".
     * Prefixes MUST be globally unique. Not sanity check is done on this so please be careful!
     * @return a string containing the unique
     */
    String getPrefix();

    /**
     * This method is responsible of executing the action logic, so it will probably dispatch to an underlying engine
     * such as a scripting engine or any other type. This makes it possible for example to implement actions in Groovy
     * or even Javascript.
     * @param action the {@link Action} to execute
     * @param event  the {@link Event} that triggered the action
     * @param actionName the name of the action to execute that is after the prefix in the action type
     * @return an integer status corresponding to what happened as defined by public constants of {@link EventService}
     */
    Integer execute(Action action, Event event, String actionName);

}
