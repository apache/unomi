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

import graphql.annotations.AnnotationsSchemaCreator;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.schema.GraphQLSchema;
import graphql.servlet.GraphQLHttpServlet;
import graphql.servlet.config.GraphQLConfiguration;
import org.apache.unomi.graphql.types.output.CDPEvent;
import org.apache.unomi.graphql.types.output.CDPEventProperties;
import org.osgi.service.component.annotations.Component;

import java.util.HashSet;
import java.util.Set;

@Component(
        service = {javax.servlet.http.HttpServlet.class, javax.servlet.Servlet.class},
        property = {"alias=/cdp_graphql"}
)
public class CDPGraphQLServlet extends GraphQLHttpServlet {

    @Override
    protected GraphQLConfiguration getConfiguration() {
        final GraphQLSchema schema = createSchema();

        return GraphQLConfiguration.with(schema).build();
    }

    private GraphQLSchema createSchema() {
        return AnnotationsSchemaCreator.newAnnotationsSchema()
                .query(RootQuery.class)
                .mutation(CDPMutation.class)
                .additionalTypes(registerAdditionalTypes())
                .setAnnotationsProcessor(createGraphQLAnnotations())
                .build();
    }

    private GraphQLAnnotations createGraphQLAnnotations() {
        final GraphQLAnnotations graphQLAnnotations = new GraphQLAnnotations();

        final ProcessingElementsContainer container = graphQLAnnotations.getContainer();

        container.setInputPrefix("");
        container.setInputSuffix("Input");

        return graphQLAnnotations;
    }

    private Set<Class<?>> registerAdditionalTypes() {
        final Set<Class<?>> additionalTypes = new HashSet<>();

        additionalTypes.add(CDPEvent.class);
        additionalTypes.add(CDPEventProperties.class);

        return additionalTypes;
    }

}
