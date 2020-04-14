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
package org.apache.unomi.graphql.schema;

import org.apache.unomi.api.Event;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.types.output.UnomiEvent;
import org.apache.unomi.graphql.utils.ReflectionUtil;
import org.osgi.service.component.annotations.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component(immediate = true, service = CDPEventInterfaceRegister.class)
public class CDPEventInterfaceRegister {

    private ConcurrentHashMap<String, Class<? extends CDPEventInterface>> events;

    public CDPEventInterfaceRegister() {
        events = new ConcurrentHashMap<>();
    }

    public void register(final Class<? extends CDPEventInterface> eventMember) {
        if (!Objects.equals(eventMember, UnomiEvent.class)) {
            events.put(getEventType(eventMember), eventMember);
        }
    }

    public CDPEventInterface getEvent(final Event event) {
        if (!events.containsKey(event.getEventType())) {
            return new UnomiEvent(event);
        }

        try {
            return events.get(event.getEventType()).getConstructor(Event.class).newInstance(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getEventType(final Class<? extends CDPEventInterface> clazz) {
        return transformEventType(ReflectionUtil.getTypeName(clazz));
    }

    private String transformEventType(final String eventType) {
        int index = eventType.indexOf("_");
        char[] chars = eventType.substring(index + 1).toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return eventType.substring(0, index).toLowerCase() + "_" + new String(chars);
    }

}
