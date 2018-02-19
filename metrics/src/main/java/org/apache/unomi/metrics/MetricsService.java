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
package org.apache.unomi.metrics;

import java.util.Map;

/**
 * This is the main interface for the metrics service, that makes it possible to count calls, callers and accumulated
 * times for sections of code.
 */
public interface MetricsService {

    /**
     * Enables or disables the metrics service.
     * @param activated if true the metrics service will be activated, false will deactivate it and clear any exists
     *                  in-memory metrics
     */
    void setActivated(boolean activated);

    boolean isActivated();

    Map<String,Boolean> getCallersStatus();

    void setCallerActivated(String timerName, boolean activated);

    boolean isCallerActivated(String timerName);

    Map<String,Metric> getMetrics();

    void resetMetrics();

    void updateTimer(String timerName, long startTime);


}
