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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.rules.Rule;

/**
 * A service that gets called when a rule's conditions are evaluated or when a rule's actions are executed.
 */
public interface RuleListenerService {

    /**
     * This enum indicates which type of already raised event we are dealing with
     */
    enum AlreadyRaisedFor {
        SESSION,
        PROFILE
    }

    /**
     * Called before a rule's conditions are evaluated. Be careful when implemented this listener because rule's condition
     * are called in very high frequencies and the performance of this listener might have a huge impact on rule's
     * performance
     * @param rule the rule that is being evaluated
     * @param event the event we are processing and evaluating against the rule
     */
    void onEvaluate(Rule rule, Event event);

    /**
     * Called when a rule has already been raised either for a session or a profile.
     * @param alreadyRaisedFor an enum that indicates if the rule was already raised once for the session or for the
     *                         profile
     * @param rule the rule that has already been raised
     * @param event the event for which this rule has already been raised.
     */
    void onAlreadyRaised(AlreadyRaisedFor alreadyRaisedFor, Rule rule, Event event);

    /**
     * Called just before a matching rule's actions are about to be executed.
     * @param rule the matching rule for the current event
     * @param event the event we are processing that matched the current rule.
     */
    void onExecuteActions(Rule rule, Event event);

}
