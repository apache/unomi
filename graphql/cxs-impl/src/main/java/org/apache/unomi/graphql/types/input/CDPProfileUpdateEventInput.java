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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.input.CDPProfileUpdateEventInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfileUpdateEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME = "CDP_ProfileUpdateEventInput";

    public static final String EVENT_NAME = "cdp_profileUpdateEvent";

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

        final Set<String> propertiesToDelete = new HashSet<>(persistedProperties.keySet());
        propertiesToDelete.removeAll(profilePropertiesAsMap.keySet());

        Map<String, Object> propertiesToAdd = new HashMap<>();
        Map<String, Object> propertiesToUpdate = new HashMap<>();
        profilePropertiesAsMap.forEach((key, value) -> {
            if (persistedProperties.containsKey(key)) {
                if (!Objects.equals(persistedProperties.get(key), profilePropertiesAsMap.get(key))) {
                    propertiesToUpdate.put("properties." + key, value);
                }
            } else {
                propertiesToAdd.put("properties." + key, value);
            }
        });

        return eventBuilder(profile)
                .setPersistent(true)
                .setPropertiesToAdd(propertiesToAdd)
                .setPropertiesToUpdate(propertiesToUpdate)
                .setPropertiesToDelete(propertiesToDelete.stream().map(prop -> "properties." + prop).collect(Collectors.toList()))
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }

}
