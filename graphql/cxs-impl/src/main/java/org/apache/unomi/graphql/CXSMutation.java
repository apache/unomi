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
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.propertytypes.CXSIdentifierPropertyType;
import org.apache.unomi.graphql.propertytypes.CXSPropertyType;
import org.apache.unomi.graphql.propertytypes.CXSSetPropertyType;
import org.apache.unomi.graphql.propertytypes.CXSStringPropertyType;

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CXS_Mutation")
public class CXSMutation {

    CXSGraphQLProvider cxsGraphQLProvider;

    public CXSMutation(CXSGraphQLProvider cxsGraphQLProvider) {
        this.cxsGraphQLProvider = cxsGraphQLProvider;
    }

    @GraphQLField
    public CXSEventType createOrUpdateEventType(DataFetchingEnvironment env, @GraphQLName("eventType") CXSEventTypeInput cxsEventTypeInput) {

        CXSEventType cxsEventType = new CXSEventType(cxsEventTypeInput.getId(), cxsEventTypeInput.getScope(), cxsEventTypeInput.getTypeName(), new ArrayList<>());
        for (CXSPropertyTypeInput propertyTypeInput : cxsEventTypeInput.getProperties()) {
            CXSPropertyType propertyType = getPropertyType(propertyTypeInput);
            cxsEventType.getProperties().add(propertyType);
        }
        cxsGraphQLProvider.getEventTypes().put(cxsEventType.getTypeName(), cxsEventType);
        cxsGraphQLProvider.updateGraphQLTypes();
        if (cxsGraphQLProvider.getCxsProviderManager() != null) {
            cxsGraphQLProvider.getCxsProviderManager().refreshProviders();
        }
        return cxsEventType;

    }

    @GraphQLField
    public int processEvents(DataFetchingEnvironment env, @GraphQLName("events") List<CXSEventInput> events) {
        return 0;
    }

    private CXSPropertyType getPropertyType(CXSPropertyTypeInput cxsPropertyTypeInput) {
        CXSPropertyType propertyType = null;
        if (cxsPropertyTypeInput.identifierPropertyTypeInput != null) {
            propertyType = getIdentifierPropertyType(cxsPropertyTypeInput.identifierPropertyTypeInput);
        } else if (cxsPropertyTypeInput.stringPropertyTypeInput != null) {
            propertyType = getStringPropertyType(cxsPropertyTypeInput.stringPropertyTypeInput);
        } else if (cxsPropertyTypeInput.setPropertyTypeInput != null) {
            propertyType = getSetPropertyType(cxsPropertyTypeInput.setPropertyTypeInput);
        }
        return propertyType;
    }

    private CXSPropertyType getSetPropertyType(CXSSetPropertyTypeInput cxsSetPropertyTypeInput) {
        List<CXSPropertyType> setProperties = null;
        if (cxsSetPropertyTypeInput.getProperties() != null) {
            setProperties = new ArrayList<>();
            for (CXSPropertyTypeInput setProperty : cxsSetPropertyTypeInput.getProperties()) {
                CXSPropertyType subPropertyType = getPropertyType(setProperty);
                if (subPropertyType != null) {
                    setProperties.add(subPropertyType);
                }
            }
        }
        return new CXSSetPropertyType(
                cxsSetPropertyTypeInput.getId(),
                cxsSetPropertyTypeInput.getName(),
                cxsSetPropertyTypeInput.getMinOccurrences(),
                cxsSetPropertyTypeInput.getMaxOccurrences(),
                cxsSetPropertyTypeInput.getTags(),
                cxsSetPropertyTypeInput.getSystemTags(),
                cxsSetPropertyTypeInput.isPersonalData(),
                setProperties);
    }

    private CXSPropertyType getStringPropertyType(CXSStringPropertyType stringPropertyType) {
        return new CXSStringPropertyType(
                stringPropertyType.getId(),
                stringPropertyType.getName(),
                stringPropertyType.getMinOccurrences(),
                stringPropertyType.getMaxOccurrences(),
                stringPropertyType.getTags(),
                stringPropertyType.getSystemTags(),
                stringPropertyType.isPersonalData(),
                stringPropertyType.getRegexp(),
                stringPropertyType.getDefaultValue()
                );
    }

    private CXSPropertyType getIdentifierPropertyType(CXSIdentifierPropertyType identifierPropertyType) {
        return new CXSIdentifierPropertyType(
                identifierPropertyType.getId(),
                identifierPropertyType.getName(),
                identifierPropertyType.getMinOccurrences(),
                identifierPropertyType.getMaxOccurrences(),
                identifierPropertyType.getTags(),
                identifierPropertyType.getSystemTags(),
                identifierPropertyType.isPersonalData(),
                identifierPropertyType.getRegexp(),
                identifierPropertyType.getDefaultValue()
        );
    }

}
