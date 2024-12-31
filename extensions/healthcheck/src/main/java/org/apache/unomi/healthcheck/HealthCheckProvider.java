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

public interface HealthCheckProvider {

    String name();

    /**
     * Used to check whether the provider is available. For example an ElasticSearch provider will not be available
     * if OpenSearch is used instead as a persistence implementation.
     * @return true if the provider is available, false otherwise
     */
    default boolean isAvailable() { return true;}

    HealthCheckResponse execute();

    default HealthCheckResponse timeout() {
        return new HealthCheckResponse.Builder().name(name()).withData("error.cause", "timeout").error().build();
    }

}
