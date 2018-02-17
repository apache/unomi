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
package org.apache.unomi.metrics.internal;

import org.apache.unomi.metrics.CalleeCount;
import org.apache.unomi.metrics.Metric;
import org.apache.unomi.metrics.MetricsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsServiceImpl implements MetricsService {

    boolean activated = false;
    Map<String,Metric> metrics = new ConcurrentHashMap<String,Metric>();
    Map<String,Boolean> calleesStatus = new ConcurrentHashMap<>();

    public void setActivated(boolean activated) {
        this.activated = activated;
        if (!activated) {
            metrics.clear();
        }
    }

    @Override
    public boolean isActivated() {
        return activated;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return metrics;
    }

    @Override
    public void resetMetrics() {
        metrics.clear();
    }

    public void updateTimer(String timerName, long startTime) {
        if (!activated) {
            return;
        }
        long totalTime = System.currentTimeMillis() - startTime;
        Metric metric = metrics.get(timerName);
        if (metric == null) {
            metric = new MetricImpl(timerName);
            metrics.put(timerName, metric);
        }
        metric.incTotalCount();
        metric.addTotalTime(totalTime);
        if (isCalleeActivated(timerName)) {
            StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
            List<String> stackTraces = new ArrayList<String>();
            if (stackTraceElements != null && stackTraceElements.length > 2) {
                // we start at index 2 to remove the internal
                for (int i = 2; i < stackTraceElements.length; i++) {
                    stackTraces.add(String.valueOf(stackTraceElements[i]));
                }
                String stackTraceHash = Integer.toString(stackTraces.hashCode());
                CalleeCount calleeCount = metric.getCalleeCounts().get(stackTraceHash);
                if (calleeCount == null) {
                    calleeCount = new CalleeCountImpl(stackTraceHash, stackTraces);
                    calleeCount.incCount();
                    calleeCount.addTime(totalTime);
                    metric.getCalleeCounts().put(stackTraceHash, calleeCount);
                } else {
                    calleeCount.incCount();
                    calleeCount.addTime(totalTime);
                }
            }
        }
    }

    @Override
    public Map<String, Boolean> getCalleesStatus() {
        return calleesStatus;
    }

    @Override
    public void setCalleeActivated(String timerName, boolean activated) {
        if (!activated) {
            if (calleesStatus.containsKey(timerName)) {
                calleesStatus.remove(timerName);
            }
        } else {
            calleesStatus.put(timerName, true);
        }
    }

    @Override
    public boolean isCalleeActivated(String timerName) {
        if (calleesStatus.containsKey(timerName)) {
            return calleesStatus.get(timerName);
        }
        if (calleesStatus.containsKey("*")) {
            return calleesStatus.get("*");
        }
        return false;
    }

}
