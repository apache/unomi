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
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.graphql.propertytypes.CDPPropertyType;

import java.util.List;

@GraphQLName("CDP_SetPropertyInput")
public class CDPSetPropertyTypeInput extends CDPPropertyType {

    @GraphQLField
    private List<CDPPropertyInput> properties;

    public CDPSetPropertyTypeInput(final @GraphQLName("name") String name,
                                   final @GraphQLName("minOccurrences") Integer minOccurrences,
                                   final @GraphQLName("maxOccurrences") Integer maxOccurrences,
                                   final @GraphQLName("tags") List<String> tags,
                                   final @GraphQLName("properties") List<CDPPropertyInput> properties) {
        super(name, minOccurrences, maxOccurrences, tags);
        this.properties = properties;
    }

    public List<CDPPropertyInput> getProperties() {
        return properties;
    }
}
