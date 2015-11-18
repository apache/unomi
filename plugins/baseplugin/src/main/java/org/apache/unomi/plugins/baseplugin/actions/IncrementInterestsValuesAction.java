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

package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;

import java.util.HashMap;
import java.util.Map;

public class IncrementInterestsValuesAction implements ActionExecutor {

    @SuppressWarnings("unchecked")
    @Override
    public int execute(Action action, Event event) {
        boolean modified = false;

        try {
            Map<String, Object> interests = (Map<String, Object>) PropertyUtils.getProperty(event, "target.properties.interests");
            if (interests != null) {
                for (Map.Entry<String, Object> s : interests.entrySet()) {
                    int value = (Integer) s.getValue();

                    HashMap<String, Object> profileInterests = (HashMap<String, Object>) event.getProfile().getProperty("interests");
                    if(profileInterests != null){
                        profileInterests = new HashMap<String, Object>(profileInterests);
                        int oldValue = (profileInterests.containsKey(s.getKey())) ? (Integer) profileInterests.get(s.getKey()) : 0;
                        profileInterests.put(s.getKey(), value + oldValue);
                    }else {
                        profileInterests = new HashMap<String, Object>();
                        profileInterests.put(s.getKey(), value);
                    }
                    event.getProfile().setProperty("interests", profileInterests);
                    modified = true;
                }
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        return modified ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
