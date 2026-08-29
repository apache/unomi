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
package org.apache.unomi.itests.persistence;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.ops4j.pax.exam.Option;

import java.io.IOException;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;

/**
 * Built-in OpenSearch backend for Unomi integration tests.
 */
public class OpenSearchITBackend implements PersistenceITBackend {

    private static final String OS_USER = "admin";
    private static final String OS_PASSWORD = "Unomi.1ntegrat10n.Tests";

    @Override
    public String providerId() {
        return PersistenceITBackendResolver.PROVIDER_OPENSEARCH;
    }

    @Override
    public Option[] featureOptions() {
        return new Option[]{
                features(
                        maven().groupId("org.apache.unomi").artifactId("unomi-kar").versionAsInProject().type("xml").classifier("features"),
                        "unomi-base",
                        "unomi-startup",
                        "unomi-opensearch-core",
                        "unomi-persistence-core",
                        "unomi-services",
                        "unomi-cxs-privacy-extension-services",
                        "unomi-plugins-base",
                        "unomi-plugins-request",
                        "unomi-plugins-mail",
                        "unomi-plugins-optimization-test",
                        "unomi-rest-api",
                        "unomi-cxs-privacy-extension",
                        "unomi-opensearch-conditions",
                        "unomi-cxs-lists-extension",
                        "unomi-cxs-geonames-extension",
                        "unomi-shell-dev-commands",
                        "unomi-wab",
                        "unomi-web-tracker",
                        "unomi-healthcheck-opensearch",
                        "unomi-router-karaf-feature",
                        "unomi-groovy-actions",
                        "unomi-rest-ui",
                        "cdp-graphql-feature",
                        // Installed explicitly (not via the distribution feature's
                        // async dependency deployment) so the DID-VC bundles are
                        // in place before the PaxExam probe starts running tests
                        "unomi-did-vc",
                        "unomi-startup-complete"
                ),
                features(
                        maven().groupId("org.apache.unomi").artifactId("unomi-distribution").versionAsInProject().type("xml").classifier("features"),
                        "unomi-distribution-opensearch-graphql"
                )
        };
    }

    @Override
    public Option[] configurationOptions() {
        String port = searchPort();
        return new Option[]{
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.cluster.name", "contextElasticSearchITests"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.addresses", "localhost:" + port),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.username", OS_USER),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.password", OS_PASSWORD),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslEnable", "false"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.sslTrustAllCertificates", "true"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.rollover.maxDocs", "300"),
                editConfigurationFilePut("etc/custom.system.properties", "org.apache.unomi.opensearch.minimalClusterState", "YELLOW"),
        };
    }

    @Override
    public String distributionFeature() {
        return "unomi-distribution-opensearch";
    }

    @Override
    public String persistenceConfigPid() {
        return "org.apache.unomi.persistence.opensearch";
    }

    @Override
    public PersistenceITCapabilities capabilities() {
        return PersistenceITCapabilities.opensearch();
    }

    @Override
    public String searchBaseUrl() {
        return "http://localhost:" + searchPort();
    }

    @Override
    public String searchPort() {
        return System.getProperty("org.apache.unomi.opensearch.addresses", "localhost:9401")
                .split(":")[1];
    }

    @Override
    public CloseableHttpClient createSearchHttpClient() throws IOException {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(OS_USER, OS_PASSWORD));
        return HttpUtils.initHttpClient(true, credentialsProvider);
    }
}
