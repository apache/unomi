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
package org.apache.unomi.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.ArrayList;
import java.util.List;

public class CXSEventTypeInput {

    private String id;
    private String scope;
    private String typeName;
    private List<CXSPropertyTypeInput> properties = new ArrayList<>();

    public CXSEventTypeInput(@GraphQLName("id") String id,
                             @GraphQLName("scope") String scope,
                             @GraphQLName("typeName") String typeName,
                             @GraphQLName("properties") List<CXSPropertyTypeInput> properties) {
        this.id = id;
        this.scope = scope;
        this.typeName = typeName;
        this.properties = properties;
    }

    @GraphQLField
    public String getId() {
        return id;
    }

    @GraphQLField
    public String getScope() {
        return scope;
    }

    @GraphQLField
    public String getTypeName() {
        return typeName;
    }

    @GraphQLField
    public List<CXSPropertyTypeInput> getProperties() {
        return properties;
    }
}
