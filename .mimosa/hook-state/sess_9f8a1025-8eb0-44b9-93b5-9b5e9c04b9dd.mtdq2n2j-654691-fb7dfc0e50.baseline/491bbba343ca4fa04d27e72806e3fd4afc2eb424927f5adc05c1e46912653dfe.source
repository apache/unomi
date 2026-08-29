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

/**
 * Contract for pluggable health checks. Implementations provide a name and
 * return a {@link HealthCheckResponse} when executed; a default timeout
 * response is available via {@link #timeout()}.
 */
public interface HealthCheckProvider {

    /**
     * Unique provider name used in responses and logs.
     *
     * @return the health check provider name
     */
    String name();

    /**
     * Executes the health check, returning a {@link HealthCheckResponse} describing status and optional data.
     *
     * @return the health check result
     */
    HealthCheckResponse execute();

    /**
     * Convenience method returning a standardized timeout error response for this provider.
     *
     * @return a timeout {@link HealthCheckResponse}
     */
    default HealthCheckResponse timeout() {
        return new HealthCheckResponse.Builder().name(name()).withData("error.cause", "timeout").error().build();
    }

}
