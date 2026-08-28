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

import org.apache.http.impl.client.CloseableHttpClient;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.ops4j.pax.exam.Option;

import java.io.IOException;

/**
 * Test-only contract for provisioning and awaiting a {@code PersistenceService} provider
 * during Pax Exam integration tests.
 * <p>
 * Built-in implementations cover Elasticsearch and OpenSearch. Additional providers register
 * via {@link java.util.ServiceLoader} or {@code -Dunomi.persistence.it.backend=<fqcn>}.
 */
public interface PersistenceITBackend {

    /** Provider id (e.g. {@code elasticsearch}, {@code opensearch}). */
    String providerId();

    /**
     * Karaf feature options for this provider (kar features + distribution feature).
     */
    Option[] featureOptions();

    /**
     * Provider-specific {@code etc/custom.system.properties} (and related) Pax Exam options.
     */
    Option[] configurationOptions();

    /** Distribution feature name passed to {@code unomi:setup -d=…}. */
    String distributionFeature();

    /**
     * Wait until the backend is usable before Unomi starts (cluster health, JDBC ping, …).
     * Default is a no-op; search backends typically poll HTTP health from {@code BaseIT}.
     * <p>
     * Called from the Pax Exam driver / early {@code @Before} path — do not assume OSGi
     * services are available yet. Prefer {@link #prepareBeforeUnomiSetup} for ConfigAdmin work.
     */
    default void awaitBackendReady() throws Exception {
    }

    /**
     * Called in the Karaf JVM after {@code UnomiManagementService} is available and before
     * {@code unomi:setup}. Use for ConfigAdmin DataSource patching, bundle restarts, etc.
     */
    default void prepareBeforeUnomiSetup(BundleContext bundleContext, ConfigurationAdmin configurationAdmin)
            throws Exception {
    }

    /** Optional verification after Unomi has started. */
    default void assertHealthyAfterUnomiStart() throws Exception {
    }

    /** ConfigAdmin PID for {@code org.apache.unomi.persistence.*} knobs (e.g. {@code throwExceptions}). */
    String persistenceConfigPid();

    PersistenceITCapabilities capabilities();

    /** HTTP base URL when {@link PersistenceITCapabilities#httpAdminApi()} is true. */
    default String searchBaseUrl() {
        throw new UnsupportedOperationException(providerId() + " does not expose an HTTP admin API");
    }

    /** Search HTTP listen port when {@link PersistenceITCapabilities#httpAdminApi()} is true. */
    default String searchPort() {
        throw new UnsupportedOperationException(providerId() + " does not expose an HTTP admin API");
    }

    /** HTTP client for admin APIs (may include basic auth). */
    default CloseableHttpClient createSearchHttpClient() throws IOException {
        throw new UnsupportedOperationException(providerId() + " does not expose an HTTP admin API");
    }
}
