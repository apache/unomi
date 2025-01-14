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
package org.apache.unomi.graphql.security;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactories;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import org.apache.unomi.api.security.SecurityService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = SchemaDirectiveWiring.class, property = {"directive=requiresRole"})
public class SecurityDirective implements SchemaDirectiveWiring {

    @Reference
    private SecurityService securityService;

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        String role = environment.getDirective().getArgument("role").getValue().toString();
        GraphQLFieldDefinition field = environment.getElement();
        GraphQLFieldsContainer parentType = environment.getFieldsContainer();

        // Create a data fetcher that first checks authorization before delegating to the original data fetcher
        DataFetcher<?> originalDataFetcher = environment.getCodeRegistry().getDataFetcher(parentType, field);
        DataFetcher<?> authDataFetcher = DataFetcherFactories.wrapDataFetcher(originalDataFetcher,
            ((dataFetchingEnvironment, value) -> {
                // Check role-based access
                if (!securityService.hasRole(role)) {
                    throw new SecurityException("User does not have required role: " + role);
                }

                // Check tenants-based access if tenants ID is provided
                String tenantId = dataFetchingEnvironment.getArgument("tenantId");
                if (tenantId != null && !securityService.hasTenantAccess(tenantId)) {
                    throw new SecurityException("User does not have access to tenants: " + tenantId);
                }

                return value;
            }));

        // Register the new data fetcher
        environment.getCodeRegistry().dataFetcher(parentType, field, authDataFetcher);
        return field;
    }
}
