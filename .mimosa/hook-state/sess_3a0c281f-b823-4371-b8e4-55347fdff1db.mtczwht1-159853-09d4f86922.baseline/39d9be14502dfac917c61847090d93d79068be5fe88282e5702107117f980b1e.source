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
import org.apache.unomi.api.Topic;
import org.junit.Assert;
import org.junit.Test;

public class GraphQLTopicIT extends BaseGraphQLIT {

    @Test
    public void testCRUD() throws Exception {
        try (CloseableHttpResponse response = post("graphql/topic/create-topic.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testTopic", context.getValue("data.cdp.createOrUpdateTopic.id"));
        }

        refreshPersistence(Topic.class);

        try (CloseableHttpResponse response = post("graphql/topic/update-topic.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testTopic", context.getValue("data.cdp.createOrUpdateTopic.id"));
            Assert.assertEquals("testTopicName Updated", context.getValue("data.cdp.createOrUpdateTopic.name"));
            Assert.assertEquals("testTopicView", context.getValue("data.cdp.createOrUpdateTopic.view.name"));
        }

        refreshPersistence(Topic.class);

        try (CloseableHttpResponse response = post("graphql/topic/get-topic.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals("testTopic", context.getValue("data.cdp.getTopic.id"));
            Assert.assertEquals("testTopicName Updated", context.getValue("data.cdp.getTopic.name"));
            Assert.assertEquals("testTopicView", context.getValue("data.cdp.getTopic.view.name"));
        }

        try (CloseableHttpResponse response = post("graphql/topic/find-topics.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertEquals(1, (int) context.getValue("data.cdp.findTopics.totalCount"));
            Assert.assertNotNull(context.getValue("data.cdp.findTopics.edges"));
        }

        try (CloseableHttpResponse response = post("graphql/topic/delete-topic.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.deleteTopic"));
        }

        refreshPersistence(Topic.class);

        try (CloseableHttpResponse response = post("graphql/topic/get-topic.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNull(context.getValue("data.cdp.getTopic"));
        }
    }

}
