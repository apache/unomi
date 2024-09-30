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
package org.apache.unomi.persistence.elasticsearch;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.http.HttpHost;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.MockNode;
import org.elasticsearch.node.Node;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

@RunWith(com.carrotsearch.randomizedtesting.RandomizedRunner.class)
@ThreadLeakScope(value = ThreadLeakScope.Scope.NONE)
public class ElasticsearchPersistenceTest {

    private static final Logger LOGGER = Logger.getLogger(ElasticsearchPersistenceTest.class.getName());

    private static final String CLUSTER_NAME = "unomi-cluster-test";
    private static final String NODE_NAME = "unomi-node-test";
    private static final String HOST = "127.0.0.1";
    private static final int HTTP_PORT_NODE_1 = 9200+10;
    private static final int HTTP_PORT_NODE_2 = 9201+10;
    private static final int TRANSPORT_PORT_NODE_1 = 9300+10;
    private static final int TRANSPORT_PORT_NODE_2 = 9301+10;

    private static RestHighLevelClient restHighLevelClient;

    private static Node node1;
    private static Node node2;

    @BeforeClass
    public static void setup() throws Exception {
        Collection plugins = Arrays.asList(Netty4Plugin.class);

        Settings settingsNode1 = Settings.builder()
                .put(ClusterName.CLUSTER_NAME_SETTING.getKey(), CLUSTER_NAME)
                .put(Node.NODE_NAME_SETTING.getKey(), NODE_NAME + "-1")
                .put(NetworkModule.HTTP_TYPE_KEY, Netty4Plugin.NETTY_HTTP_TRANSPORT_NAME)
                .put(Environment.PATH_HOME_SETTING.getKey(), "target/data-1")
                .put(Environment.PATH_DATA_SETTING.getKey(), "target/data-1")
                .put("network.host", HOST)
                .put("http.port", HTTP_PORT_NODE_1)
                .put(NetworkModule.TRANSPORT_TYPE_KEY, Netty4Plugin.NETTY_TRANSPORT_NAME)
                .put("transport.port", TRANSPORT_PORT_NODE_1)
                .build();
        node1 = new MockNode(settingsNode1, plugins);
        node1.start();

        Settings settingsNode2 = Settings.builder()
                .put(ClusterName.CLUSTER_NAME_SETTING.getKey(), CLUSTER_NAME)
                .put(Node.NODE_NAME_SETTING.getKey(), NODE_NAME + "-2")
                .put(NetworkModule.HTTP_TYPE_KEY, Netty4Plugin.NETTY_HTTP_TRANSPORT_NAME)
                .put(Environment.PATH_HOME_SETTING.getKey(), "target/data-2")
                .put(Environment.PATH_DATA_SETTING.getKey(), "target/data-2")
                .put("network.host", HOST)
                .put("http.port", HTTP_PORT_NODE_2)
                .put(NetworkModule.TRANSPORT_TYPE_KEY, Netty4Plugin.NETTY_TRANSPORT_NAME)
                .put("transport.port", TRANSPORT_PORT_NODE_2)
                .build();
        node2 = new MockNode(settingsNode2, plugins);
        node2.start();

        restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                new HttpHost(HOST, HTTP_PORT_NODE_1, "http"),
                new HttpHost(HOST, HTTP_PORT_NODE_2, "http")));
    }

    @AfterClass
    public static void teardown() throws Exception {
        IOUtils.close(restHighLevelClient);
        if (node1 != null) {
            node1.close();
        }
        if (node2 != null) {
            node2.close();
        }
    }

    @Test
    public void testGetClusterInfo() throws Exception {
        MainResponse response = restHighLevelClient.info(RequestOptions.DEFAULT);
        LOGGER.info("Cluster getMinimumIndexCompatibilityVersion: " + response.getVersion().getMinimumIndexCompatibilityVersion());
        LOGGER.info("Cluster getMinimumWireCompatibilityVersion: " + response.getVersion().getMinimumWireCompatibilityVersion());
        LOGGER.info("Cluster number: " + response.getVersion().getNumber());
    }

    @Test
    public void testCreateIndex() throws Exception {
        restHighLevelClient.info(RequestOptions.DEFAULT.toBuilder().addHeader("name", "value").build());
        final String indexName = "unomi-index-" + new Date().getTime();
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        CreateIndexResponse response = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
        if (response.isAcknowledged()) {
            LOGGER.info(">>> Create index :: ok :: name = " + response.index());
        } else {
            LOGGER.info(">>> Create index :: not acknowledged");
        }

//        ClusterHealthResponse actionGet = restHighLevelClient.cluster()
//                .health(Requests.clusterHealthRequest("unomi-index-1").waitForGreenStatus().waitForEvents(Priority.LANGUID)
//                .waitForNoRelocatingShards(true), RequestOptions.DEFAULT);
//        Assert.assertNotNull(actionGet);
//
//        switch (actionGet.getStatus()) {
//            case GREEN:
//                logger.info(">>> Cluster State :: GREEN");
//                break;
//            case YELLOW:
//                logger.info(">>> Cluster State :: YELLOW");
//                break;
//            case RED:
//                logger.info(">>> Cluster State :: RED");
//                break;
//        }
//        Assert.assertNotEquals(actionGet.getStatus(), ClusterHealthStatus.RED);

        IndexRequest indexRequest = new IndexRequest(indexName);
        indexRequest.id(UUID.randomUUID().toString());
        String type = "{\"type\":\"unomi-type\"}";
        String source = "{\"name\":\"unomi-name\"}";
        indexRequest.source(XContentType.JSON, type, source);
        ActionRequestValidationException exception = indexRequest.validate();
        Assert.assertNull(exception);

        IndexResponse indexResponse = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        Assert.assertNotNull(indexResponse);
        if (indexResponse.status() == RestStatus.CREATED) {
            LOGGER.info(">>> Insert data created");
        } else {
            LOGGER.info(">>> Insert data ko :: " + indexResponse.status().name());
        }
        Assert.assertEquals(indexResponse.status(), RestStatus.CREATED);
    }

}
