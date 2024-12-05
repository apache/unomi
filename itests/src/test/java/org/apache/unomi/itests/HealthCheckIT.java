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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.interceptor.security.AccessDeniedException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.karaf.itests.KarafTestSupport;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * Health Check Integration Tests
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class HealthCheckIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(HealthCheckIT.class);

    protected static final String HEALTHCHECK_AUTH_USER_NAME = "health";
    protected static final String HEALTHCHECK_AUTH_PASSWORD = "health";
    protected static final String HEALTHCHECK_ENDPOINT = "/health/check";

    @Test
    public void testHealthCheck() {
        try {
            List<HealthCheckResponse> response = get(HEALTHCHECK_ENDPOINT, new TypeReference<>() {});
            LOGGER.info("health check response: {}", response);
            Assert.assertEquals(5, response.size());
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("karaf") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> {
                if (r.getName().equals("elasticsearch") && r.getStatus() == HealthCheckResponse.Status.LIVE) {
                    Map<String, Object> data = r.getData();
                    String clusterName = (String) data.get("clusterName");
                    String expectedClusterName = isOpenSearch() ? "docker-cluster" : "contextElasticSearchITests";
                    return clusterName != null && clusterName.equals(expectedClusterName);
                }
                return false;
            }));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("unomi") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("cluster") && r.getStatus() == HealthCheckResponse.Status.LIVE));
            Assert.assertTrue(response.stream().anyMatch(r -> r.getName().equals("persistence") && r.getStatus() == HealthCheckResponse.Status.LIVE));
        } catch (Exception e) {
            LOGGER.error("Error while executing health check", e);
            fail("Error while executing health check" + e.getMessage());
        }
    }

    protected <T> T get(final String url, TypeReference<T> typeReference) {
        CloseableHttpResponse response = null;
        try {
            final HttpGet httpGet = new HttpGet(getFullUrl(url));
            response = executeHttpRequest(httpGet);
            if (response.getStatusLine().getStatusCode() == 200) {
                return objectMapper.readValue(response.getEntity().getContent(), typeReference);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public BasicCredentialsProvider getHttpClientCredentialProvider() {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(HEALTHCHECK_AUTH_USER_NAME, HEALTHCHECK_AUTH_PASSWORD));
        return credsProvider;
    }

    public static class HealthCheckResponse {
        private String name;
        private Status status;
        private long collectingTime;
        private Map<String, Object> data;

        public HealthCheckResponse() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public long getCollectingTime() {
            return collectingTime;
        }

        public void setCollectingTime(long collectingTime) {
            this.collectingTime = collectingTime;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }

        @Override public String toString() {
            return "HealthCheckResponse{" + "name='" + name + '\'' + ", status=" + status + ", collectingTime=" + collectingTime + ", data="
                    + data + '}';
        }

        public enum Status {
            DOWN,     //Not available
            UP,       //Running or starting
            LIVE,     //Ready to serve requests
            ERROR     //Errors during check
        }
    }
}
