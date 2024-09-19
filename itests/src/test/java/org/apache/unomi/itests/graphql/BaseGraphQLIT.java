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

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;
import org.apache.unomi.itests.BaseIT;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public abstract class BaseGraphQLIT extends BaseIT {

    protected static final ContentType JSON_CONTENT_TYPE = ContentType.create("application/json");

    protected CloseableHttpResponse postAnonymous(final String resource) throws Exception {
        return postAs(resource, null, null);
    }

    protected CloseableHttpResponse post(final String resource) throws Exception {
        return postAs(resource, "karaf", "karaf");
    }

    protected CloseableHttpResponse postAs(final String resource, final String username, final String password) throws Exception {
        final String resourceAsString = resourceAsString(resource);

        final HttpPost request = new HttpPost(getFullUrl("/graphql"));

        request.setEntity(new StringEntity(resourceAsString, JSON_CONTENT_TYPE));

        if (username != null && password != null) {
            String basicAuth = username + ":" + password;
            String wrap = "Basic " + new String(Base64.getEncoder().encode(basicAuth.getBytes()));
            request.setHeader("Authorization", wrap);
        } else {
            request.removeHeaders("Authorization");
        }

        return HttpClientBuilder.create().build().execute(request);
    }

    protected String resourceAsString(final String resource) {
        final java.net.URL url = bundleContext.getBundle().getResource(resource);
        try (InputStream stream = url.openStream()) {
            final GraphQLObjectMapper objectMapper = GraphQLObjectMapper.getInstance();
            return objectMapper.writeValueAsString(objectMapper.readTree(stream));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected static class ResponseContext {

        private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

        private static final Pattern INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

        private final Map<String, Object> responseAsMap;

        private ResponseContext(HttpEntity httpEntity) {
            try {
                final String jsonFromResponse = EntityUtils.toString(httpEntity);
                responseAsMap = GraphQLObjectMapper.getInstance().readValue(jsonFromResponse, Map.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static ResponseContext parse(HttpEntity httpEntity) {
            return new ResponseContext(httpEntity);
        }

        public <T> T getValue(final String path) {
            final String[] nodePaths = path.split(DOT_PATTERN.pattern(), -1);

            Map<String, Object> fieldsAsMap = responseAsMap;

            T result = null;

            for (int i = 0; i < nodePaths.length; i++) {
                if (fieldsAsMap == null) {
                    break;
                } else if (i != nodePaths.length - 1) {
                    Matcher matcher = INDEX_PATTERN.matcher(nodePaths[i]);
                    if (matcher.find()) {
                        final String key = nodePaths[i].replaceAll(INDEX_PATTERN.pattern(), "");
                        final int index = Integer.parseInt(matcher.group(1));
                        fieldsAsMap = (Map<String, Object>) (((List) fieldsAsMap.get(key)).get(index));
                    } else {
                        fieldsAsMap = (Map<String, Object>) fieldsAsMap.get(nodePaths[i]);
                    }
                } else {
                    result = (T) fieldsAsMap.get(nodePaths[i]);
                }
            }

            return result;
        }

        public Map<String, Object> getResponseAsMap() {
            return responseAsMap;
        }

    }

}
