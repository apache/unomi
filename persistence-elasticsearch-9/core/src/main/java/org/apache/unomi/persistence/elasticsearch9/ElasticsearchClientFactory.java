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
package org.apache.unomi.persistence.elasticsearch9;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.net.ssl.SSLContext;
import java.util.List;

public class ElasticsearchClientFactory {

    public static ElasticsearchClient createClient(
            List<HttpHost> hosts,
            Integer socketTimeout,
            SSLContext sslContext,
            String username,
            String password) {

        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));

        if (socketTimeout != null) {
            builder.setRequestConfigCallback(requestConfigBuilder ->
                    requestConfigBuilder.setSocketTimeout(socketTimeout));
        }

        if (sslContext != null) {
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setSSLContext(sslContext));
        }

        if (username != null && password != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                if (sslContext != null) {
                    httpClientBuilder.setSSLContext(sslContext);
                }
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            });
        }

        RestClient restClient = builder.build();

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(ESCustomObjectMapper.getObjectMapper()));

        return new ElasticsearchClient(transport);
    }

    public static ClientBuilder builder() {
        return new ClientBuilder();
    }

    public static class ClientBuilder {
        private List<HttpHost> hosts;
        private Integer socketTimeout;
        private SSLContext sslContext;
        private String username;
        private String password;

        public ClientBuilder hosts(List<HttpHost> hosts) {
            this.hosts = hosts;
            return this;
        }

        public ClientBuilder socketTimeout(Integer socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public ClientBuilder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public ClientBuilder usernameAndPassword(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public ElasticsearchClient build() {
            return createClient(hosts, socketTimeout, sslContext, username, password);
        }
    }
}