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

package org.apache.unomi.api.query;

import java.io.Serializable;

/**
 * Named inclusive numeric bucket for query filters and aggregations.
 * Stores a {@code key} plus {@code from}/{@code to} bounds so conditions can
 * test profile or event properties against numeric intervals.
 */
public class NumericRange implements Serializable {
    private String key;
    private Double from;
    private Double to;

    /**
     * Creates an empty numeric range.
     */
    public NumericRange() {
    }

    /**
     * Label for this range in query results.
     *
     * @return range key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the range label.
     *
     * @param key range key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Inclusive lower bound.
     *
     * @return lower bound, or {@code null} if unset
     */
    public Double getFrom() {
        return from;
    }

    /**
     * Sets the inclusive lower bound.
     *
     * @param from lower bound
     */
    public void setFrom(Double from) {
        this.from = from;
    }

    /**
     * Inclusive upper bound.
     *
     * @return upper bound, or {@code null} if unset
     */
    public Double getTo() {
        return to;
    }

    /**
     * Sets the inclusive upper bound.
     *
     * @param to upper bound
     */
    public void setTo(Double to) {
        this.to = to;
    }
}
