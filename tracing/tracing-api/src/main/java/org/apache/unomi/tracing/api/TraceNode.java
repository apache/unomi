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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node in the hierarchical request trace returned to operators.
 * Each node records an operation name, timing, optional context/result strings,
 * log lines, and nested child nodes. The tracing service assembles these into
 * a tree attached to context and event processing responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceNode implements Serializable {
    private String operationType;
    private String description;
    private String context;
    private String result;
    private long startTime;
    private long endTime;
    private List<String> traces;
    private List<TraceNode> children;

    /**
     * Creates an empty trace node with empty trace and child lists.
     */
    public TraceNode() {
        this.traces = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    /**
     * Returns the type of operation represented by this node.
     *
     * @return the operation type
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Sets the type of operation represented by this node.
     *
     * @param operationType the operation type
     */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * Returns the human-readable description of this operation.
     *
     * @return the operation description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the human-readable description of this operation.
     *
     * @param description the operation description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns additional context information for this operation.
     *
     * @return the operation context
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets additional context information for this operation.
     *
     * @param context the operation context
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Returns the result summary for this operation.
     *
     * @return the operation result
     */
    public String getResult() {
        return result;
    }

    /**
     * Sets the result summary for this operation.
     *
     * @param result the operation result
     */
    public void setResult(String result) {
        this.result = result;
    }

    /**
     * Returns the start time of this operation in milliseconds.
     *
     * @return the start time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Sets the start time of this operation in milliseconds.
     *
     * @param startTime the start time
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Returns the end time of this operation in milliseconds.
     *
     * @return the end time
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Sets the end time of this operation in milliseconds.
     *
     * @param endTime the end time
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
     * Returns the operation duration in milliseconds.
     *
     * @return duration as {@code endTime - startTime}, or zero if end time precedes start time
     */
    public long getDuration() {
        return Math.max(0, endTime - startTime);
    }

    /**
     * Adds a trace message to this node.
     *
     * @param trace the trace message to add
     */
    public void addTrace(String trace) {
        this.traces.add(trace);
    }

    /**
     * Returns the trace messages recorded for this node.
     *
     * @return an unmodifiable view of trace messages
     */
    public List<String> getTraces() {
        return Collections.unmodifiableList(traces);
    }

    /**
     * Replaces the trace messages for this node.
     *
     * @param traces the new trace message list
     */
    public void setTraces(List<String> traces) {
        this.traces = traces;
    }

    /**
     * Adds a child trace node under this node.
     *
     * @param child the child trace node to add
     */
    public void addChild(TraceNode child) {
        this.children.add(child);
    }

    /**
     * Returns the child trace nodes.
     *
     * @return an unmodifiable view of child nodes
     */
    public List<TraceNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Replaces the child trace nodes.
     *
     * @param children the new child node list
     */
    public void setChildren(List<TraceNode> children) {
        this.children = children;
    }
}