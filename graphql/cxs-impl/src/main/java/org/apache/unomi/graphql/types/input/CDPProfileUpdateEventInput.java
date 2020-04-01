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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.input.CDPProfileUpdateEventInput.TYPE_NAME_INTERNAL;

@GraphQLName(TYPE_NAME_INTERNAL)
public class CDPProfileUpdateEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME_INTERNAL = "CDP_ProfileUpdateEvent";

    public static final String TYPE_NAME = TYPE_NAME_INTERNAL + "Input";

    public static final String EVENT_NAME = "cdp_profileUpdateEvent";

    public CDPProfileUpdateEventInput() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public Event buildEvent(
            final LinkedHashMap<String, Object> eventInputAsMap,
            final DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }

        final LinkedHashMap<String, Object> profilePropertiesAsMap = (LinkedHashMap<String, Object>) eventInputAsMap.get(EVENT_NAME);

        final Map<String, Object> persistedProperties = profile.getProperties();

        final Map<String, Object> propertyToUpdate = profilePropertiesAsMap.entrySet().stream()
                .filter(e -> persistedProperties.containsKey(e.getKey())
                        && !Objects.equals(persistedProperties.get(e.getKey()), profilePropertiesAsMap.get(e.getKey())))
                .collect(Collectors.toMap(e -> "properties." + e.getKey(), Map.Entry::getValue));

        final Set<String> propertyToDelete = new HashSet<>(persistedProperties.keySet());
        propertyToDelete.removeAll(profilePropertiesAsMap.keySet());

        return eventBuilder(profile)
                .setPropertiesToUpdate(propertyToUpdate)
                .setPropertiesToDelete(propertyToDelete.stream().map(prop -> "properties." + prop).collect(Collectors.toList()))
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }

}
