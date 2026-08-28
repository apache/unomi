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

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

import java.util.LinkedHashMap;

/*
*   Processor to handle all unomi defined event types
*/
public class CDPUnomiEventInput extends BaseProfileEventProcessor {

    @Override
    public Event buildEvent(
            final LinkedHashMap<String, Object> eventInputAsMap,
            final DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }
        //TODO: maybe will have to convert manually
        return CustomObjectMapper.getObjectMapper().convertValue(eventInputAsMap, Event.class);
    }

    @Override
    public String getFieldName() {
        return "_allUnomiEventTypes_";
    }

}
