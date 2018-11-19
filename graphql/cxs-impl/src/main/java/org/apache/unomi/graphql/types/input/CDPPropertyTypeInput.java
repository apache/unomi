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
import org.apache.unomi.graphql.propertytypes.*;

@GraphQLName("CDP_PropertyType")
public class CDPPropertyTypeInput {

    public CDPPropertyTypeInput(
            @GraphQLName("identifier") CDPIdentifierPropertyType identifierPropertyTypeInput,
            @GraphQLName("string") CDPStringPropertyType stringPropertyTypeInput,
            @GraphQLName("int") CDPIntPropertyType integerPropertyTypeInput,
            @GraphQLName("float") CDPFloatPropertyType floatPropertyTypeInput,
            @GraphQLName("date") CDPDatePropertyType datePropertyTypeInput,
            @GraphQLName("boolean") CDPBooleanPropertyType booleanPropertyTypeInput,
            @GraphQLName("geopoint") CDPGeoPointPropertyType geoPointPropertyTypeInput,
            @GraphQLName("set") CDPSetPropertyTypeInput setPropertyTypeInput) {
        this.identifierPropertyTypeInput = identifierPropertyTypeInput;
        this.stringPropertyTypeInput = stringPropertyTypeInput;
        this.integerPropertyTypeInput = integerPropertyTypeInput;
        this.floatPropertyTypeInput = floatPropertyTypeInput;
        this.datePropertyTypeInput = datePropertyTypeInput;
        this.booleanPropertyTypeInput = booleanPropertyTypeInput;
        this.geoPointPropertyTypeInput = geoPointPropertyTypeInput;
        this.setPropertyTypeInput = setPropertyTypeInput;
    }

    @GraphQLField
    @GraphQLName("identifier")
    public CDPIdentifierPropertyType identifierPropertyTypeInput;

    @GraphQLField
    @GraphQLName("string")
    public CDPStringPropertyType stringPropertyTypeInput;

    @GraphQLField
    @GraphQLName("int")
    public CDPIntPropertyType integerPropertyTypeInput;

    @GraphQLField
    @GraphQLName("float")
    public CDPFloatPropertyType floatPropertyTypeInput;

    @GraphQLField
    @GraphQLName("date")
    public CDPDatePropertyType datePropertyTypeInput;

    @GraphQLField
    @GraphQLName("boolean")
    public CDPBooleanPropertyType booleanPropertyTypeInput;

    @GraphQLField
    @GraphQLName("geopoint")
    public CDPGeoPointPropertyType geoPointPropertyTypeInput;

    @GraphQLField
    @GraphQLName("set")
    public CDPSetPropertyTypeInput setPropertyTypeInput;
}
