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
import org.apache.unomi.graphql.types.input.property.BaseCDPPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPBooleanPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPDatePropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPFloatPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPGeoPointPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPIdentifierPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPIntPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPLongPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPSetPropertyInput;
import org.apache.unomi.graphql.types.input.property.CDPStringPropertyInput;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@GraphQLName("CDP_PropertyInput")
public class CDPPropertyInput {

    @GraphQLField
    @GraphQLName("identifier")
    private CDPIdentifierPropertyInput identifierPropertyTypeInput;

    @GraphQLField
    @GraphQLName("string")
    private CDPStringPropertyInput stringPropertyTypeInput;

    @GraphQLField
    @GraphQLName("int")
    private CDPIntPropertyInput integerPropertyTypeInput;

    @GraphQLField
    @GraphQLName("long")
    private CDPLongPropertyInput longPropertyTypeInput;

    @GraphQLField
    @GraphQLName("float")
    private CDPFloatPropertyInput floatPropertyTypeInput;

    @GraphQLField
    @GraphQLName("date")
    private CDPDatePropertyInput datePropertyTypeInput;

    @GraphQLField
    @GraphQLName("boolean")
    private CDPBooleanPropertyInput booleanPropertyTypeInput;

    @GraphQLField
    @GraphQLName("geopoint")
    private CDPGeoPointPropertyInput geoPointPropertyTypeInput;

    @GraphQLField
    @GraphQLName("set")
    private CDPSetPropertyInput setPropertyTypeInput;

    public CDPPropertyInput(
            final @GraphQLName("identifier") CDPIdentifierPropertyInput identifierPropertyTypeInput,
            final @GraphQLName("string") CDPStringPropertyInput stringPropertyTypeInput,
            final @GraphQLName("int") CDPIntPropertyInput integerPropertyTypeInput,
            final @GraphQLName("long") CDPLongPropertyInput longPropertyTypeInput,
            final @GraphQLName("float") CDPFloatPropertyInput floatPropertyTypeInput,
            final @GraphQLName("date") CDPDatePropertyInput datePropertyTypeInput,
            final @GraphQLName("boolean") CDPBooleanPropertyInput booleanPropertyTypeInput,
            final @GraphQLName("geopoint") CDPGeoPointPropertyInput geoPointPropertyTypeInput,
            final @GraphQLName("set") CDPSetPropertyInput setPropertyTypeInput) {
        this.identifierPropertyTypeInput = identifierPropertyTypeInput;
        this.stringPropertyTypeInput = stringPropertyTypeInput;
        this.integerPropertyTypeInput = integerPropertyTypeInput;
        this.longPropertyTypeInput = longPropertyTypeInput;
        this.floatPropertyTypeInput = floatPropertyTypeInput;
        this.datePropertyTypeInput = datePropertyTypeInput;
        this.booleanPropertyTypeInput = booleanPropertyTypeInput;
        this.geoPointPropertyTypeInput = geoPointPropertyTypeInput;
        this.setPropertyTypeInput = setPropertyTypeInput;
    }

    public CDPIdentifierPropertyInput getIdentifierPropertyTypeInput() {
        return identifierPropertyTypeInput;
    }

    public CDPStringPropertyInput getStringPropertyTypeInput() {
        return stringPropertyTypeInput;
    }

    public CDPIntPropertyInput getIntegerPropertyTypeInput() {
        return integerPropertyTypeInput;
    }

    public CDPLongPropertyInput getLongPropertyTypeInput() {
        return longPropertyTypeInput;
    }

    public CDPFloatPropertyInput getFloatPropertyTypeInput() {
        return floatPropertyTypeInput;
    }

    public CDPDatePropertyInput getDatePropertyTypeInput() {
        return datePropertyTypeInput;
    }

    public CDPBooleanPropertyInput getBooleanPropertyTypeInput() {
        return booleanPropertyTypeInput;
    }

    public CDPGeoPointPropertyInput getGeoPointPropertyTypeInput() {
        return geoPointPropertyTypeInput;
    }

    public CDPSetPropertyInput getSetPropertyTypeInput() {
        return setPropertyTypeInput;
    }

    public BaseCDPPropertyInput getProperty() {
        final List<BaseCDPPropertyInput> properties = Arrays.asList(
                identifierPropertyTypeInput,
                stringPropertyTypeInput,
                integerPropertyTypeInput,
                longPropertyTypeInput,
                floatPropertyTypeInput,
                datePropertyTypeInput,
                booleanPropertyTypeInput,
                geoPointPropertyTypeInput,
                setPropertyTypeInput);

        return properties.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

}
