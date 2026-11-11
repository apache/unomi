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
package org.apache.unomi.graphql.schema;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.apache.unomi.graphql.limits.GraphQLLimitsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs real graphql-java execution with the depth and complexity instrumentation that
 * {@link GraphQLSchemaUpdater} installs, using the shipped default limits.
 */
class GraphQLLimitsInstrumentationTest {

    private static final String SDL = "type Query { node: Node }\n"
            + "type Node { id: ID, child: Node, children: [Node] }";

    /** The shipped defaults (no configuration applied yet). */
    private final GraphQLLimitsConfig defaults = new GraphQLLimitsConfig();

    private static GraphQL graphQL(Instrumentation instrumentation) {
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse(SDL), RuntimeWiring.newRuntimeWiring().build());
        GraphQL.Builder builder = GraphQL.newGraphQL(schema);
        if (instrumentation != null) {
            builder.instrumentation(instrumentation);
        }
        return builder.build();
    }

    private ExecutionResult runWithDefaults(String query) {
        return graphQL(GraphQLSchemaUpdater.buildInstrumentation(defaults)).execute(query);
    }

    /** GraphiQL sends the standard introspection query; the defaults must leave it working. */
    @Test
    void defaults_allowIntrospection() {
        ExecutionResult result = runWithDefaults(IntrospectionQuery.INTROSPECTION_QUERY);

        assertTrue(result.getErrors().isEmpty(), () -> "introspection refused: " + result.getErrors());
    }

    @Test
    void defaults_allowOrdinaryQuery() {
        ExecutionResult result = runWithDefaults("{ node { id child { id children { id } } } }");

        assertTrue(result.getErrors().isEmpty(), () -> "query refused: " + result.getErrors());
    }

    @Test
    void defaults_refuseQueryDeeperThanLimit() {
        ExecutionResult result = runWithDefaults(nested(defaults.getMaxQueryDepth() + 5));

        assertFalse(result.getErrors().isEmpty(), "an over-deep query must be refused");
    }

    /** Many aliased fields at shallow depth: refused by complexity, not by depth. */
    @Test
    void defaults_refuseQueryMoreComplexThanLimit() {
        StringBuilder query = new StringBuilder("{");
        int aliases = defaults.getMaxQueryComplexity() / 2 + 10;  // each alias costs 2 (node + id)
        for (int i = 0; i < aliases; i++) {
            query.append(" a").append(i).append(": node { id }");
        }
        query.append(" }");

        ExecutionResult result = runWithDefaults(query.toString());

        assertFalse(result.getErrors().isEmpty(), "an over-complex query must be refused");
    }

    /** Without the instrumentation the same deep query runs, so the refusal above comes from it. */
    @Test
    void withoutInstrumentation_deepQueryRuns() {
        ExecutionResult result = graphQL(null).execute(nested(defaults.getMaxQueryDepth() + 5));

        assertTrue(result.getErrors().isEmpty(), () -> "unexpected errors: " + result.getErrors());
    }

    /** Zero disables a check; with both disabled no instrumentation is installed. */
    @Test
    void zeroDisablesBothLimits() {
        GraphQLLimitsConfig disabled = mock(GraphQLLimitsConfig.class);
        when(disabled.getMaxQueryDepth()).thenReturn(0);
        when(disabled.getMaxQueryComplexity()).thenReturn(0);

        assertNull(GraphQLSchemaUpdater.buildInstrumentation(disabled));
        assertNull(GraphQLSchemaUpdater.buildInstrumentation(null));
    }

    /** { node { child { child ... { id } } } } with the given depth of selections. */
    private static String nested(int depth) {
        StringBuilder query = new StringBuilder("{ node {");
        for (int i = 1; i < depth; i++) {
            query.append(" child {");
        }
        query.append(" id");
        for (int i = 0; i < depth; i++) {
            query.append(" }");
        }
        return query.append(" }").toString();
    }
}
