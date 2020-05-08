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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.itests.BaseIT;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class GraphQLSegmentIT extends BaseIT {

    private static final String GRAPHQL_ENDPOINT = URL + "/graphql";

    @Inject
    protected BundleContext bundleContext;

    @Inject
    @Filter(timeout = 600000)
    protected BundleWatcher bundleWatcher;

    @Before
    public void setUp() throws InterruptedException {
        while (!bundleWatcher.isStartupComplete()) {
            Thread.sleep(1000);
        }
    }

    @Test
    public void testCreateSegment() throws IOException, InterruptedException {
        HttpPost request = new HttpPost(GRAPHQL_ENDPOINT);

        String jsonRequest = parseJson(bundleContext.getBundle().getResource("graphql/create-segment.json"));

        System.out.println(jsonRequest);

        request.setEntity(new StringEntity(jsonRequest, ContentType.create("application/json")));

        try (CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request)) {
            String jsonFromResponse = EntityUtils.toString(response.getEntity());

            Assert.assertNotNull("Response can not be null", jsonFromResponse);

            System.out.println(jsonFromResponse);
        }
    }

    private String parseJson(final URL url) {
        try (InputStream stream = url.openStream()) {
            return convertInputStreamToString(stream);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String convertInputStreamToString(InputStream inputStream)
            throws IOException {

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
