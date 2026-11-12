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

package org.apache.unomi.graphql.servlet;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET may only execute operations positively identified as queries.
 */
class GraphQLServletReadOnlyOperationTest {

    @Test
    void allowsQueries() {
        assertTrue(GraphQLServlet.isReadOnlyOperation("query { cdp { getProfile } }", null));
        assertTrue(GraphQLServlet.isReadOnlyOperation("{ cdp { getProfile } }", null));
    }

    @Test
    void refusesMutations() {
        assertFalse(GraphQLServlet.isReadOnlyOperation("mutation { cdp { deleteProfile } }", null));
    }

    @Test
    void refusesSubscriptions() {
        assertFalse(GraphQLServlet.isReadOnlyOperation("subscription { cdp { eventListener } }", null));
    }

    @Test
    void selectsOperationByName() {
        final String document = "query readIt { cdp { getProfile } } mutation writeIt { cdp { deleteProfile } }";
        assertTrue(GraphQLServlet.isReadOnlyOperation(document, "readIt"));
        assertFalse(GraphQLServlet.isReadOnlyOperation(document, "writeIt"));
    }

    @Test
    void refusesMutationAsFirstAnonymousChoice() {
        assertFalse(GraphQLServlet.isReadOnlyOperation(
                "mutation writeIt { cdp { deleteProfile } } query readIt { cdp { getProfile } }", null));
    }

    @Test
    void refusesUnparsableDocuments() {
        assertFalse(GraphQLServlet.isReadOnlyOperation("mutation { unterminated", null));
    }

    @Test
    void allowsEmptyQueryToFailDownstream() {
        assertTrue(GraphQLServlet.isReadOnlyOperation(null, null));
        assertTrue(GraphQLServlet.isReadOnlyOperation("   ", null));
    }

    // getProfile(createIfMissing: true) saves a profile, so a query setting it is not read-only.

    private static final String CREATE = "query { cdp { getProfile(profileID: {id: \"x\"}, createIfMissing: true) { id } } }";

    @Test
    void refusesCreateIfMissingLiteralTrue() {
        assertFalse(GraphQLServlet.isReadOnlyOperation(CREATE, null));
    }

    @Test
    void allowsCreateIfMissingFalseOrAbsent() {
        assertTrue(GraphQLServlet.isReadOnlyOperation(CREATE.replace("true", "false"), null));
        assertTrue(GraphQLServlet.isReadOnlyOperation(
                "query { cdp { getProfile(profileID: {id: \"x\"}, createIfMissing: null) { id } } }", null));
        assertTrue(GraphQLServlet.isReadOnlyOperation("query { cdp { getProfile(profileID: {id: \"x\"}) { id } } }", null));
    }

    @Test
    void refusesCreateIfMissingThroughVariable() {
        final String document = "query q($c: Boolean) { cdp { getProfile(profileID: {id: \"x\"}, createIfMissing: $c) { id } } }";
        assertFalse(GraphQLServlet.isReadOnlyOperation(document, null, Map.of("c", true)));
        assertTrue(GraphQLServlet.isReadOnlyOperation(document, null, Map.of("c", false)));
        assertTrue(GraphQLServlet.isReadOnlyOperation(document, null, Collections.emptyMap()));
    }

    @Test
    void refusesCreateIfMissingThroughVariableDefault() {
        final String document = "query q($c: Boolean = true) { cdp { getProfile(profileID: {id: \"x\"}, createIfMissing: $c) { id } } }";
        assertFalse(GraphQLServlet.isReadOnlyOperation(document, null, Collections.emptyMap()));
        assertTrue(GraphQLServlet.isReadOnlyOperation(document, null, Map.of("c", false)));
    }

    @Test
    void refusesCreateIfMissingInsideFragments() {
        assertFalse(GraphQLServlet.isReadOnlyOperation(
                "query { cdp { ...p } } fragment p on CDP_Query { getProfile(profileID: {id: \"x\"}, createIfMissing: true) { id } }",
                null));
        assertFalse(GraphQLServlet.isReadOnlyOperation(
                "query { cdp { ... on CDP_Query { getProfile(profileID: {id: \"x\"}, createIfMissing: true) { id } } } }", null));
    }

    @Test
    void cyclicFragmentsDoNotLoop() {
        assertTrue(GraphQLServlet.isReadOnlyOperation(
                "query { cdp { ...a } } fragment a on CDP_Query { ...b } fragment b on CDP_Query { ...a }", null));
    }
}
