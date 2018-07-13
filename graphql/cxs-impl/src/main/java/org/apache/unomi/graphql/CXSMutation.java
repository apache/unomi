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

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CXS_Mutation")
public class CXSMutation {

    @GraphQLField
    public CXSEventType createOrUpdateEventType(DataFetchingEnvironment env, @GraphQLName("eventType") CXSEventTypeInput cxsEventTypeInput) {

        CXSGraphQLProvider cxsGraphQLProvider = null;
        CXSEventType cxsEventType = new CXSEventType();
        cxsEventType.id = cxsEventTypeInput.id;
        cxsEventType.typeName = cxsEventTypeInput.scope;
        cxsEventType.properties = new ArrayList<>();
        for (CXSPropertyTypeInput propertyTypeInput : cxsEventTypeInput.properties) {
            CXSPropertyType propertyType = getPropertyType(propertyTypeInput);
            cxsEventType.properties.add(propertyType);
        }
        cxsGraphQLProvider.getEventTypes().put(cxsEventType.typeName, cxsEventType);
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
        CXSSetPropertyType cxsSetPropertyType = new CXSSetPropertyType();

        populateCommonProperties(cxsSetPropertyTypeInput, cxsSetPropertyType);
        if (cxsSetPropertyTypeInput.properties != null) {
            List<CXSPropertyType> setProperties = new ArrayList<>();
            for (CXSPropertyTypeInput setProperty : cxsSetPropertyTypeInput.properties) {
                CXSPropertyType subPropertyType = getPropertyType(setProperty);
                if (subPropertyType != null) {
                    setProperties.add(subPropertyType);
                }
            }
            cxsSetPropertyType.properties = setProperties;
        }
        return cxsSetPropertyType;
    }

    private CXSPropertyType getStringPropertyType(CXSStringPropertyType stringPropertyType) {
        CXSStringPropertyType cxsStringPropertyType = new CXSStringPropertyType();
        populateCommonProperties(stringPropertyType, cxsStringPropertyType);
        cxsStringPropertyType.defaultValue = stringPropertyType.defaultValue;
        cxsStringPropertyType.regexp = stringPropertyType.regexp;
        return cxsStringPropertyType;
    }

    private CXSPropertyType getIdentifierPropertyType(CXSIdentifierPropertyType identifierPropertyType) {
        CXSIdentifierPropertyType cxsIdentifierPropertyType = new CXSIdentifierPropertyType();
        populateCommonProperties(identifierPropertyType, cxsIdentifierPropertyType);
        cxsIdentifierPropertyType.defaultValue = identifierPropertyType.defaultValue;
        cxsIdentifierPropertyType.regexp = identifierPropertyType.regexp;
        return cxsIdentifierPropertyType;
    }

    private void populateCommonProperties(CXSPropertyType source, CXSPropertyType destination) {
        if (source == null) {
            return;
        }
        destination.id = source.id;
        destination.name = source.name;
        destination.personalData = source.personalData;
        destination.systemTags = source.systemTags;
        destination.tags = source.tags;
        destination.minOccurrences = source.minOccurrences;
        destination.maxOccurrences = source.maxOccurrences;
    }

}
