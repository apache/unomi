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
import org.apache.unomi.graphql.propertytypes.*;

@GraphQLName("CXS_PropertyTypeInput")
public class CXSPropertyTypeInput {

    public CXSPropertyTypeInput(
            @GraphQLName("identifier") CXSIdentifierPropertyType identifierPropertyTypeInput,
            @GraphQLName("string") CXSStringPropertyType stringPropertyTypeInput,
            @GraphQLName("int") CXSIntPropertyType integerPropertyTypeInput,
            @GraphQLName("float") CXSFloatPropertyType floatPropertyTypeInput,
            @GraphQLName("date") CXSDatePropertyType datePropertyTypeInput,
            @GraphQLName("boolean") CXSBooleanPropertyType booleanPropertyTypeInput,
            @GraphQLName("geopoint") CXSGeoPointPropertyType geoPointPropertyTypeInput,
            @GraphQLName("set") CXSSetPropertyTypeInput setPropertyTypeInput) {
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
    public CXSIdentifierPropertyType identifierPropertyTypeInput;

    @GraphQLField
    @GraphQLName("string")
    public CXSStringPropertyType stringPropertyTypeInput;

    @GraphQLField
    @GraphQLName("int")
    public CXSIntPropertyType integerPropertyTypeInput;

    @GraphQLField
    @GraphQLName("float")
    public CXSFloatPropertyType floatPropertyTypeInput;

    @GraphQLField
    @GraphQLName("date")
    public CXSDatePropertyType datePropertyTypeInput;

    @GraphQLField
    @GraphQLName("boolean")
    public CXSBooleanPropertyType booleanPropertyTypeInput;

    @GraphQLField
    @GraphQLName("geopoint")
    public CXSGeoPointPropertyType geoPointPropertyTypeInput;

    @GraphQLField
    @GraphQLName("set")
    public CXSSetPropertyTypeInput setPropertyTypeInput;
}
