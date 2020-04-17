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
package org.apache.unomi.graphql.utils;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventBuilder {

    private final Profile profile;
    private final String eventType;
    private Map<String, Object> propertiesToUpdate;
    private List<String> propertiesToDelete;
    private Map<String, Object> properties = new HashMap<>();
    private boolean persistent;

    public EventBuilder(final String eventType, final Profile profile) {
        this.eventType = eventType;
        this.profile = profile;
    }

    public static EventBuilder create(final String eventType, Profile profile) {
        return new EventBuilder(eventType, profile);
    }

    public EventBuilder setPropertiesToUpdate(Map<String, Object> propertiesToUpdate) {
        this.propertiesToUpdate = propertiesToUpdate;
        return this;
    }

    public EventBuilder setPropertiesToDelete(List<String> propertiesToDelete) {
        this.propertiesToDelete = propertiesToDelete;
        return this;
    }

    public EventBuilder setPersistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    public EventBuilder setProperty(final String property, final Object value) {
        this.properties.put(property, value);
        return this;
    }

    public Event build() {
        final Event event = new Event(eventType, null, profile, null, null, profile, new Date());
        event.setPersistent(persistent);
        event.setProperty("targetId", profile.getItemId());
        event.setProperty("targetType", Profile.ITEM_TYPE);
        if (propertiesToUpdate != null) {
            event.setProperty("update", propertiesToUpdate);
        }
        if (propertiesToDelete != null) {
            event.setProperty("delete", propertiesToDelete);
        }
        if (!properties.isEmpty()) {
            event.setProperties(properties);
        }
        return event;
    }

}
