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

import org.apache.unomi.api.Session;

public class SessionHardcodedPropertyAccessor extends HardcodedPropertyAccessor<Session> {

    public SessionHardcodedPropertyAccessor(HardcodedPropertyAccessorRegistry registry) {
        super(registry);
    }

    @Override
    Object getProperty(Session object, String propertyName, String leftoverExpression) {
        if ("duration".equals(propertyName)) {
            return object.getDuration();
        }
        if ("size".equals(propertyName)) {
            return object.getSize();
        }
        if ("lastEventDate".equals(propertyName)) {
            return object.getLastEventDate();
        }
        if ("properties".equals(propertyName)) {
            return registry.getProperty(object.getProperties(), leftoverExpression);
        }
        if ("systemProperties".equals(propertyName)) {
            return registry.getProperty(object.getSystemProperties(), leftoverExpression);
        }
        if ("profile".equals(propertyName)) {
            return registry.getProperty(object.getProfile(), leftoverExpression);
        }
        if ("profileId".equals(propertyName)) {
            return object.getProfileId();
        }
        return PROPERTY_NOT_FOUND_MARKER;
    }
}
