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
package org.apache.unomi.graphql.types.input.property;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.PropertyType;

import java.util.List;

@GraphQLName("CDP_StringPropertyInput")
public class CDPStringPropertyInput extends BaseCDPPropertyInput {

    private String regexp;
    private String defaultValue;

    public CDPStringPropertyInput(@GraphQLName("name") String name,
                                  @GraphQLName("minOccurrences") Integer minOccurrences,
                                  @GraphQLName("maxOccurrences") Integer maxOccurrences,
                                  @GraphQLName("tags") List<String> tags,
                                  @GraphQLName("regexp") String regexp,
                                  @GraphQLName("defaultValue") String defaultValue) {
        super(name, minOccurrences, maxOccurrences, tags);
        this.regexp = regexp;
        this.defaultValue = defaultValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public String getRegexp() {
        return regexp;
    }

    @GraphQLField
    @GraphQLPrettify
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getCDPPropertyType() {
        return "string";
    }

    @Override
    public void updateType(final PropertyType type) {
        if (type == null) {
            return;
        }
        super.updateType(type);
        type.setDefaultValue(defaultValue);
        //TODO save regexp
    }
}
