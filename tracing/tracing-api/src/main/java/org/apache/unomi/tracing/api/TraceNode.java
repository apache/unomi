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
package org.apache.unomi.tracing.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the request tracing tree structure.
 * Each node contains information about an operation, its timing, and any child operations.
 */
public class TraceNode implements Serializable {
    private String operationType;
    private String description;
    private String context;
    private String result;
    private long startTime;
    private long endTime;
    private List<String> traces;
    private List<TraceNode> children;

    public TraceNode() {
        this.traces = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return Math.max(0, endTime - startTime);
    }

    public void addTrace(String trace) {
        this.traces.add(trace);
    }

    public List<String> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    public void setTraces(List<String> traces) {
        this.traces = traces;
    }

    public void addChild(TraceNode child) {
        this.children.add(child);
    }

    public List<TraceNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void setChildren(List<TraceNode> children) {
        this.children = children;
    }
}