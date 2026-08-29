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
package org.apache.unomi.plugins.baseplugin.conditions.accessors;

import org.apache.unomi.api.Event;
import org.apache.unomi.plugins.baseplugin.conditions.HardcodedPropertyAccessorRegistry;

public class EventAccessor extends HardcodedPropertyAccessor<Event> {

    public EventAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    public Object getProperty(Event object, String propertyName, String leftoverExpression) {
        if ("properties".equals(propertyName)) {
            return registry.getProperty(object.getProperties(), leftoverExpression);
        }
        if ("flattenedProperties".equals(propertyName)) {
            return registry.getProperty(object.getFlattenedProperties(), leftoverExpression);
        }
        if ("eventType".equals(propertyName)) {
            return object.getEventType();
        }
        if ("profile".equals(propertyName)) {
            return registry.getProperty(object.getProfile(), leftoverExpression);
        }
        if ("profileId".equals(propertyName)) {
            return object.getProfileId();
        }
        if ("session".equals(propertyName)) {
            return registry.getProperty(object.getSession(), leftoverExpression);
        }
        if ("sessionId".equals(propertyName)) {
            return object.getSessionId();
        }
        if ("source".equals(propertyName)) {
            return registry.getProperty(object.getSource(), leftoverExpression);
        }
        if ("target".equals(propertyName)) {
            return registry.getProperty(object.getTarget(), leftoverExpression);
        }
        return PROPERTY_NOT_FOUND_MARKER;
    }
}
