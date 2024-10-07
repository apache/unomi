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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Health Check response.
 */
public class HealthCheckResponse {

    private final String name;
    private final Status status;
    private final long collectingTime;
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

    public enum Status {
        DOWN,     //Not available
        UP,       //Running or starting
        LIVE,     //Ready to serve requests
        ERROR     //Errors during check
    }

}
