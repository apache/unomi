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

/**
 * Backend-agnostic IT capabilities. Describe <em>what</em> the harness and tests may
 * rely on (HTTP admin surface, rollover API shape, health probes), not which vendor
 * product is installed.
 * <p>
 * Built-in Elasticsearch / OpenSearch factories set these to match today’s search
 * engines; JDBC and other providers typically use {@link #none()}.
 */
public final class PersistenceITCapabilities {

    /**
     * HTTP API shape used to inspect or assert index rollover policy wiring.
     * Named after the protocol, not a vendor.
     */
    public enum IndexRolloverApi {
        /** No rollover HTTP API (typical for SQL / embedded stores). */
        NONE,
        /**
         * Lifecycle-policy HTTP API (paths/settings of the form {@code _ilm},
         * {@code index.lifecycle.*}).
         */
        LIFECYCLE,
        /**
         * State-management-policy HTTP API (paths/settings of the form
         * {@code _plugins/_ism}, {@code index_state_management.*}).
         */
        STATE_MANAGEMENT;

        public boolean isPresent() {
            return this != NONE;
        }
    }

    /**
     * Expected outcome of range queries on flattened / nested document properties.
     */
    public enum FlattenedRangeQueryResult {
        /** Query succeeds with null or an empty hit list. */
        EMPTY,
        /** Query may return matching documents. */
        HITS
    }

    private final boolean httpAdminApi;
    private final boolean providerNamedHealthProbe;
    private final boolean clusterHealthProbe;
    private final IndexRolloverApi indexRolloverApi;
    private final boolean snapshotRestoreMigration;
    private final FlattenedRangeQueryResult flattenedRangeQueryResult;

    private PersistenceITCapabilities(Builder builder) {
        this.httpAdminApi = builder.httpAdminApi;
        this.providerNamedHealthProbe = builder.providerNamedHealthProbe;
        this.clusterHealthProbe = builder.clusterHealthProbe;
        this.indexRolloverApi = builder.indexRolloverApi;
        this.snapshotRestoreMigration = builder.snapshotRestoreMigration;
        this.flattenedRangeQueryResult = builder.flattenedRangeQueryResult;
    }

    /**
     * Backend exposes HTTP admin APIs for cluster/index/snapshot operations
     * (typical of search engines). When false, {@code BaseIT} skips HTTP health
     * prep and search helpers refuse to run.
     */
    public boolean httpAdminApi() {
        return httpAdminApi;
    }

    /**
     * Health check includes a probe whose name equals the persistence provider id.
     */
    public boolean providerNamedHealthProbe() {
        return providerNamedHealthProbe;
    }

    /**
     * Health check includes a cluster-level probe (name {@code cluster}).
     */
    public boolean clusterHealthProbe() {
        return clusterHealthProbe;
    }

    /**
     * Rollover policy HTTP API shape, or {@link IndexRolloverApi#NONE}.
     */
    public IndexRolloverApi indexRolloverApi() {
        return indexRolloverApi;
    }

    /**
     * Backend can exercise legacy snapshot-restore migration fixtures
     * (search-engine snapshot HTTP + restore into current indices).
     */
    public boolean snapshotRestoreMigration() {
        return snapshotRestoreMigration;
    }

    /**
     * How range queries on flattened properties behave for assertions.
     */
    public FlattenedRangeQueryResult flattenedRangeQueryResult() {
        return flattenedRangeQueryResult;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** No optional capabilities — JDBC, embedded, or incomplete providers. */
    public static PersistenceITCapabilities none() {
        return builder().build();
    }

    /** @deprecated use {@link #none()} */
    @Deprecated
    public static PersistenceITCapabilities nonSearchBackend() {
        return none();
    }

    public static PersistenceITCapabilities elasticsearch() {
        return builder()
                .httpAdminApi(true)
                .providerNamedHealthProbe(true)
                .clusterHealthProbe(true)
                .indexRolloverApi(IndexRolloverApi.LIFECYCLE)
                .snapshotRestoreMigration(true)
                .flattenedRangeQueryResult(FlattenedRangeQueryResult.EMPTY)
                .build();
    }

    public static PersistenceITCapabilities opensearch() {
        return builder()
                .httpAdminApi(true)
                .providerNamedHealthProbe(true)
                .clusterHealthProbe(true)
                .indexRolloverApi(IndexRolloverApi.STATE_MANAGEMENT)
                .snapshotRestoreMigration(false)
                .flattenedRangeQueryResult(FlattenedRangeQueryResult.HITS)
                .build();
    }

    public static final class Builder {
        private boolean httpAdminApi;
        private boolean providerNamedHealthProbe;
        private boolean clusterHealthProbe;
        private IndexRolloverApi indexRolloverApi = IndexRolloverApi.NONE;
        private boolean snapshotRestoreMigration;
        private FlattenedRangeQueryResult flattenedRangeQueryResult = FlattenedRangeQueryResult.EMPTY;

        public Builder httpAdminApi(boolean httpAdminApi) {
            this.httpAdminApi = httpAdminApi;
            return this;
        }

        public Builder providerNamedHealthProbe(boolean providerNamedHealthProbe) {
            this.providerNamedHealthProbe = providerNamedHealthProbe;
            return this;
        }

        public Builder clusterHealthProbe(boolean clusterHealthProbe) {
            this.clusterHealthProbe = clusterHealthProbe;
            return this;
        }

        public Builder indexRolloverApi(IndexRolloverApi indexRolloverApi) {
            this.indexRolloverApi = indexRolloverApi != null ? indexRolloverApi : IndexRolloverApi.NONE;
            return this;
        }

        public Builder snapshotRestoreMigration(boolean snapshotRestoreMigration) {
            this.snapshotRestoreMigration = snapshotRestoreMigration;
            return this;
        }

        public Builder flattenedRangeQueryResult(FlattenedRangeQueryResult flattenedRangeQueryResult) {
            this.flattenedRangeQueryResult = flattenedRangeQueryResult != null
                    ? flattenedRangeQueryResult
                    : FlattenedRangeQueryResult.EMPTY;
            return this;
        }

        public PersistenceITCapabilities build() {
            return new PersistenceITCapabilities(this);
        }
    }
}
