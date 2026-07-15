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

package org.apache.unomi.healthcheck;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One liveliness check result returned by GET /health/check.
 */
public class HealthCheckResponse {

    /**
     * Name of the check. Built-ins include {@code karaf} (always present) plus
     * enabled providers such as {@code elasticsearch}, {@code opensearch},
     * {@code cluster}, {@code persistence}, and {@code unomi} (bundle state).
     * @api.example elasticsearch
     */
    private final String name;
    /**
     * Check outcome. One of: {@code LIVE} (ready to serve traffic),
     * {@code UP} (running or still starting), {@code DOWN} (unavailable),
     * {@code ERROR} (the check itself failed). The HTTP endpoint returns 200
     * only when every item is {@code LIVE}; otherwise 206.
     * @api.example LIVE
     */
    private final Status status;
    /**
     * Time spent collecting this check, in milliseconds.
     * @api.example 42
     */
    private final long collectingTime;
    /**
     * Optional provider-specific details; omitted from JSON when empty.
     * Values are strings, numbers, or booleans. Common keys:
     * {@code error} / {@code error.cause} on failure or timeout;
     * elasticsearch/opensearch: {@code cluster_name}, {@code status}
     * (green/yellow/red), {@code timed_out}, {@code number_of_nodes},
     * {@code number_of_data_nodes}, {@code active_primary_shards},
     * {@code active_shards}, {@code relocating_shards},
     * {@code initializing_shards}, {@code unassigned_shards};
     * cluster: {@code cluster.size}, {@code cluster.node.<i>.uptime},
     * {@code cluster.node.<i>.cpuload}, {@code cluster.node.<i>.loadAverage},
     * {@code cluster.node.<i>.public}, {@code cluster.node.<i>.internal},
     * {@code cluster.node.<i>.role}.
     * @api.example {"cluster_name":"elasticsearch","status":"green","number_of_nodes":1}
     */
    private final Map<String, Object> data;

    protected HealthCheckResponse(String name, Status status, long collectingTime, Map<String, Object> data) {
        this.name = name;
        this.status = status;
        this.collectingTime = collectingTime;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public long getCollectingTime() {
        return collectingTime;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder named(String name) {
        return new Builder().name(name);
    }

    public static HealthCheckResponse up(String name) {
        return named(name).up().build();
    }

    public static HealthCheckResponse live(String name) {
        return named(name).live().build();
    }

    public static HealthCheckResponse down(String name) {
        return named(name).down().build();
    }

    public static HealthCheckResponse error(String name) {
        return named(name).error().build();
    }

    @JsonIgnore
    public boolean isLive() {
        return this.status == Status.LIVE;
    }

    @JsonIgnore
    public boolean isUp() {
        return this.status == Status.UP;
    }

    @JsonIgnore
    public boolean isDown() {
        return this.status == Status.DOWN;
    }

    @JsonIgnore
    public boolean isError() {
        return this.status == Status.ERROR;
    }

    public static class Builder {
        private final long borntime;
        private String name;
        private HealthCheckResponse.Status status;
        private final Map<String, Object> data;

        public Builder() {
            this.borntime = System.currentTimeMillis();
            this.status = Status.DOWN;
            this.data = new LinkedHashMap<>();
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder withData(String key, String value) {
            this.data.put(key, value);
            return this;
        }

        public Builder withData(String key, long value) {
            this.data.put(key, value);
            return this;
        }

        public Builder withData(String key, boolean value) {
            this.data.put(key, value);
            return this;
        }

        public Builder up() {
            this.status = Status.UP;
            return this;
        }

        public Builder live() {
            this.status = Status.LIVE;
            return this;
        }

        public Builder down() {
            this.status = Status.DOWN;
            return this;
        }

        public Builder error() {
            this.status = Status.ERROR;
            return this;
        }

        public HealthCheckResponse build() {
            return new HealthCheckResponse(this.name, this.status, (System.currentTimeMillis() - borntime), this.data.isEmpty() ? null : this.data);
        }
    }

    /**
     * Outcome of a single health check.
     */
    public enum Status {
        /** Not available / not ready. */
        DOWN,
        /** Running or still starting (not yet ready to serve traffic). */
        UP,
        /** Ready to serve requests; required on every check for HTTP 200. */
        LIVE,
        /** The check itself failed (see {@code data.error} / {@code data.error.cause}). */
        ERROR
    }

}
