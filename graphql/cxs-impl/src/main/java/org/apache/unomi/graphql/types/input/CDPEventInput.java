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

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

@GraphQLName("CDP_Event")
public class CDPEventInput {

    @GraphQLID
    @GraphQLField
    private String id;

    @GraphQLField
    private String cdp_sourceID;

    @GraphQLField
    @GraphQLNonNull
    private CDPProfileIDInput cdp_profileID;

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    private String cdp_objectID;

    public CDPEventInput(
            @GraphQLID @GraphQLName("id") String id,
            @GraphQLName("cdp_sourceID") String cdp_sourceID,
            @GraphQLNonNull @GraphQLName("cdp_profileID") CDPProfileIDInput cdp_profileID,
            @GraphQLID @GraphQLNonNull @GraphQLName("cdp_objectID") String cdp_objectID) {
        this.id = id;
        this.cdp_sourceID = cdp_sourceID;
        this.cdp_profileID = cdp_profileID;
        this.cdp_objectID = cdp_objectID;
    }

    public String getId() {
        return id;
    }

    public String getCdp_sourceID() {
        return cdp_sourceID;
    }

    public CDPProfileIDInput getCdp_profileID() {
        return cdp_profileID;
    }

    public String getCdp_objectID() {
        return cdp_objectID;
    }

}
