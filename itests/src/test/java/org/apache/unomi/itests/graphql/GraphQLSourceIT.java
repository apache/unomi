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
package org.apache.unomi.itests.graphql;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.unomi.api.Scope;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphQLSourceIT extends BaseGraphQLIT {

    @Test
    public void testCRUD() throws Exception {
        try (CloseableHttpResponse response = post("graphql/source/create-source.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            assertEquals("testSourceId", context.getValue("data.cdp.createOrUpdateSource.id"));
            assertNull(context.getValue("data.cdp.createOrUpdateSource.thirdParty"));
        }

        refreshPersistence(Scope.class);

        try (CloseableHttpResponse response = post("graphql/source/update-source.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            assertEquals("testSourceId", context.getValue("data.cdp.createOrUpdateSource.id"));
            assertTrue(context.getValue("data.cdp.createOrUpdateSource.thirdParty"));
        }

        refreshPersistence(Scope.class);

        try (CloseableHttpResponse response = post("graphql/source/get-sources.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            assertEquals("testSourceId", context.getValue("data.cdp.getSources[0].id"));
            assertTrue(context.getValue("data.cdp.getSources[0].thirdParty"));
        }

        try (CloseableHttpResponse response = post("graphql/source/delete-source.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            assertTrue(context.getValue("data.cdp.deleteSource"));
        }
    }

}
