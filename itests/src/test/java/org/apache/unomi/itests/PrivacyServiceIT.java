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
 * limitations under the License
 */
package org.apache.unomi.itests;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PrivacyServiceIT extends BaseIT {

    protected static final String PRIVACY_ENDPOINT = "/cxs/privacy";
    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;

    @Before
    public void setUp() throws InterruptedException, IOException {
        keepTrying("Couldn't find privacy endpoint",
                () -> get(PRIVACY_ENDPOINT + "/info", Map.class),
                serverInfo -> serverInfo != null,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testServerInfo() throws IOException {
        Map<String,Object> serverInfo = get(PRIVACY_ENDPOINT + "/info", Map.class);
        assertNotNull("Server info is null", serverInfo);
        assertEquals("Server identifier is incorrect", "Apache Unomi", serverInfo.get("serverIdentifier"));
    }

    @Test
    public void testServerInfos() throws IOException {
        List<Map<String,Object>> serverInfos = get(PRIVACY_ENDPOINT + "/infos", List.class);
        assertEquals("Server info list is invalid", 1, serverInfos.size());
        assertEquals("Server identifier is incorrect", "Apache Unomi", serverInfos.get(0).get("serverIdentifier"));
    }

}
