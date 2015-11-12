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

package org.oasis_open.contextserver.plugins.baseplugin.actions;

import org.joda.time.DateTime;
import org.joda.time.Years;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;

/**
 * Created by kevan on 11/08/15.
 */
public class EvaluateProfileAgeAction implements ActionExecutor {

    @Override
    public int execute(Action action, Event event) {
        boolean updated = false;
        if(event.getProfile().getProperty("birthDate") != null) {
            Integer y = Years.yearsBetween(new DateTime(event.getProfile().getProperty("birthDate")), new DateTime()).getYears();
            if(event.getProfile().getProperty("age") == null || event.getProfile().getProperty("age") != y){
                updated = true;
                event.getProfile().setProperty("age", y);
            }
        }
        return updated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
