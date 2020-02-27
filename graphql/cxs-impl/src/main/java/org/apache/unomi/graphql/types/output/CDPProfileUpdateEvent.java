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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.Date;
import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPProfileUpdateEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfileUpdateEvent extends CDPEventInterface {

    public static final String TYPE_NAME = "CDP_ProfileUpdateEvent";

    public CDPProfileUpdateEvent(
            @GraphQLID @GraphQLNonNull String id,
            CDPSource cdp_source,
            CDPClient cdp_client,
            @GraphQLNonNull CDPProfileID cdp_profileID,
            @GraphQLNonNull CDPProfile cdp_profile,
            @GraphQLNonNull CDPObject cdp_object,
            CDPGeoPoint cdp_location,
            Date cdp_timestamp,
            List<CDPTopic> cdp_topics) {
        super(id, cdp_source, cdp_client, cdp_profileID, cdp_profile, cdp_object, cdp_location, cdp_timestamp, cdp_topics);
    }

}
