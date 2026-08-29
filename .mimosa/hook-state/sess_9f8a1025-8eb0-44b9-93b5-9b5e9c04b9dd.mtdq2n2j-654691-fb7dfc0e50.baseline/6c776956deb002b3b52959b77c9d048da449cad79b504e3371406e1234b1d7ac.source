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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Resolves the active {@link PersistenceITBackend} for the IT harness.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>{@code -Dunomi.persistence.it.backend=<fully.qualified.ClassName>}</li>
 *   <li>{@link ServiceLoader} implementations matching the provider id</li>
 *   <li>Built-in Elasticsearch / OpenSearch backends</li>
 * </ol>
 * Provider id comes from {@code unomi.persistence.provider}, falling back to the
 * deprecated {@code unomi.search.engine} alias (default {@code elasticsearch}).
 */
public final class PersistenceITBackendResolver {

    public static final String PROVIDER_PROPERTY = "unomi.persistence.provider";
    /** @deprecated use {@link #PROVIDER_PROPERTY}; kept for existing scripts and CI. */
    @Deprecated
    public static final String SEARCH_ENGINE_PROPERTY = "unomi.search.engine";
    public static final String BACKEND_CLASS_PROPERTY = "unomi.persistence.it.backend";

    public static final String PROVIDER_ELASTICSEARCH = "elasticsearch";
    public static final String PROVIDER_OPENSEARCH = "opensearch";

    private PersistenceITBackendResolver() {
    }

    public static String resolveProviderId() {
        String provider = System.getProperty(PROVIDER_PROPERTY);
        if (provider != null && !provider.isBlank()) {
            return provider.trim();
        }
        return System.getProperty(SEARCH_ENGINE_PROPERTY, PROVIDER_ELASTICSEARCH).trim();
    }

    public static PersistenceITBackend resolve() {
        String explicitClass = System.getProperty(BACKEND_CLASS_PROPERTY);
        if (explicitClass != null && !explicitClass.isBlank()) {
            return instantiate(explicitClass.trim());
        }

        String providerId = resolveProviderId();
        Map<String, PersistenceITBackend> byId = loadBackendsById();
        PersistenceITBackend backend = byId.get(providerId);
        if (backend != null) {
            return backend;
        }

        throw new IllegalArgumentException(
                "No PersistenceITBackend registered for provider '" + providerId + "'. "
                        + "Set -D" + PROVIDER_PROPERTY + "=<id> (or deprecated -D" + SEARCH_ENGINE_PROPERTY + "), "
                        + "register a ServiceLoader implementation, or set -D" + BACKEND_CLASS_PROPERTY + "=<fqcn>. "
                        + "Known built-in / discovered ids: " + byId.keySet());
    }

    private static Map<String, PersistenceITBackend> loadBackendsById() {
        Map<String, PersistenceITBackend> byId = new LinkedHashMap<>();
        // Built-ins first so ServiceLoader can override the same id if desired.
        register(byId, new ElasticsearchITBackend());
        register(byId, new OpenSearchITBackend());
        for (PersistenceITBackend backend : ServiceLoader.load(PersistenceITBackend.class)) {
            register(byId, backend);
        }
        return byId;
    }

    private static void register(Map<String, PersistenceITBackend> byId, PersistenceITBackend backend) {
        byId.put(backend.providerId(), backend);
    }

    private static PersistenceITBackend instantiate(String fqcn) {
        try {
            Class<?> clazz = Class.forName(fqcn);
            if (!PersistenceITBackend.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(fqcn + " does not implement PersistenceITBackend");
            }
            return (PersistenceITBackend) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Failed to instantiate PersistenceITBackend " + fqcn, e);
        }
    }

    /** Visible for tests / diagnostics. */
    static List<String> knownProviderIds() {
        return new ArrayList<>(loadBackendsById().keySet());
    }
}
