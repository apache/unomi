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
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_EventType")
public class CDPEventTypeInput {

    @GraphQLField
    private String id;

    @GraphQLField
    private String scope;

    @GraphQLField
    private String typeName;

    @GraphQLField
    private List<CDPPropertyInput> properties = new ArrayList<>();

    public CDPEventTypeInput(@GraphQLName("id") String id,
                             @GraphQLName("scope") String scope,
                             @GraphQLName("typeName") String typeName,
                             @GraphQLName("properties") List<CDPPropertyInput> properties) {
        this.id = id;
        this.scope = scope;
        this.typeName = typeName;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public List<CDPPropertyInput> getProperties() {
        return properties;
    }

    public void setProperties(List<CDPPropertyInput> properties) {
        this.properties = properties;
    }

}
