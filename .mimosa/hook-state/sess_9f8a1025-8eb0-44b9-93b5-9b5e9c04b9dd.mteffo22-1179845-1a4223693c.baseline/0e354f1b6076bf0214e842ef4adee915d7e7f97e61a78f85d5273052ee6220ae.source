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
package org.apache.unomi.graphql.types.output.property;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.schema.CDPPropertyInterfaceRegister;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPPropertyInterface;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.output.property.CDPSetPropertyType.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPSetPropertyType extends CDPPropertyType implements CDPPropertyInterface {

    public static final String TYPE_NAME = "CDP_SetProperty";

    public static final String UNOMI_TYPE = "set";

    public CDPSetPropertyType(final PropertyType type) {
        super(type);
    }

    @GraphQLField
    public List<CDPPropertyInterface> properties(final DataFetchingEnvironment environment) {
        final Set<PropertyType> childPropertyTypes = this.type.getChildPropertyTypes();
        if (childPropertyTypes == null || childPropertyTypes.isEmpty()) {
            return null;
        }

        final ServiceManager serviceManager = environment.getContext();
        return childPropertyTypes.stream()
                .map(prop -> serviceManager.getService(CDPPropertyInterfaceRegister.class).getProperty(prop))
                .collect(Collectors.toList());
    }

}
