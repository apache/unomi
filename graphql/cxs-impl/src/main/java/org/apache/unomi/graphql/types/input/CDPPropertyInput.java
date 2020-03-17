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
import org.apache.unomi.graphql.propertytypes.CDPBooleanPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPDatePropertyType;
import org.apache.unomi.graphql.propertytypes.CDPFloatPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPGeoPointPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPIdentifierPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPIntPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPPropertyType;
import org.apache.unomi.graphql.propertytypes.CDPStringPropertyType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@GraphQLName("CDP_Property")
public class CDPPropertyInput {

    @GraphQLField
    @GraphQLName("identifier")
    private CDPIdentifierPropertyType identifierPropertyTypeInput;

    @GraphQLField
    @GraphQLName("string")
    private CDPStringPropertyType stringPropertyTypeInput;

    @GraphQLField
    @GraphQLName("int")
    private CDPIntPropertyType integerPropertyTypeInput;

    @GraphQLField
    @GraphQLName("float")
    private CDPFloatPropertyType floatPropertyTypeInput;

    @GraphQLField
    @GraphQLName("date")
    private CDPDatePropertyType datePropertyTypeInput;

    @GraphQLField
    @GraphQLName("boolean")
    private CDPBooleanPropertyType booleanPropertyTypeInput;

    @GraphQLField
    @GraphQLName("geopoint")
    private CDPGeoPointPropertyType geoPointPropertyTypeInput;

    @GraphQLField
    @GraphQLName("set")
    private CDPSetPropertyTypeInput setPropertyTypeInput;

    public CDPPropertyInput(
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

    public CDPIdentifierPropertyType getIdentifierPropertyTypeInput() {
        return identifierPropertyTypeInput;
    }

    public void setIdentifierPropertyTypeInput(CDPIdentifierPropertyType identifierPropertyTypeInput) {
        this.identifierPropertyTypeInput = identifierPropertyTypeInput;
    }

    public CDPStringPropertyType getStringPropertyTypeInput() {
        return stringPropertyTypeInput;
    }

    public void setStringPropertyTypeInput(CDPStringPropertyType stringPropertyTypeInput) {
        this.stringPropertyTypeInput = stringPropertyTypeInput;
    }

    public CDPIntPropertyType getIntegerPropertyTypeInput() {
        return integerPropertyTypeInput;
    }

    public void setIntegerPropertyTypeInput(CDPIntPropertyType integerPropertyTypeInput) {
        this.integerPropertyTypeInput = integerPropertyTypeInput;
    }

    public CDPFloatPropertyType getFloatPropertyTypeInput() {
        return floatPropertyTypeInput;
    }

    public void setFloatPropertyTypeInput(CDPFloatPropertyType floatPropertyTypeInput) {
        this.floatPropertyTypeInput = floatPropertyTypeInput;
    }

    public CDPDatePropertyType getDatePropertyTypeInput() {
        return datePropertyTypeInput;
    }

    public void setDatePropertyTypeInput(CDPDatePropertyType datePropertyTypeInput) {
        this.datePropertyTypeInput = datePropertyTypeInput;
    }

    public CDPBooleanPropertyType getBooleanPropertyTypeInput() {
        return booleanPropertyTypeInput;
    }

    public void setBooleanPropertyTypeInput(CDPBooleanPropertyType booleanPropertyTypeInput) {
        this.booleanPropertyTypeInput = booleanPropertyTypeInput;
    }

    public CDPGeoPointPropertyType getGeoPointPropertyTypeInput() {
        return geoPointPropertyTypeInput;
    }

    public void setGeoPointPropertyTypeInput(CDPGeoPointPropertyType geoPointPropertyTypeInput) {
        this.geoPointPropertyTypeInput = geoPointPropertyTypeInput;
    }

    public CDPSetPropertyTypeInput getSetPropertyTypeInput() {
        return setPropertyTypeInput;
    }

    public void setSetPropertyTypeInput(CDPSetPropertyTypeInput setPropertyTypeInput) {
        this.setPropertyTypeInput = setPropertyTypeInput;
    }

    public CDPPropertyType getProperty() {
        final List<CDPPropertyType> properties = Arrays.asList(
                identifierPropertyTypeInput,
                stringPropertyTypeInput,
                integerPropertyTypeInput,
                floatPropertyTypeInput,
                datePropertyTypeInput,
                booleanPropertyTypeInput,
                geoPointPropertyTypeInput,
                setPropertyTypeInput);

        return properties.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

}
