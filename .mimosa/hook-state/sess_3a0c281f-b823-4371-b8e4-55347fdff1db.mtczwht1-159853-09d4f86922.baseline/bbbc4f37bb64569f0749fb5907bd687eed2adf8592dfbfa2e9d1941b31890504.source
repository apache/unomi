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
 * Named inclusive date bucket for queries and aggregations.
 * Combines a display {@code key} with {@code from} and {@code to} bounds so
 * segment conditions and aggregate queries can group events or profiles inside
 * a calendar window.
 */
public class DateRange implements Serializable {
    private String key;
    private Object from;
    private Object to;

    /**
     * Creates an empty date range.
     */
    public DateRange() {
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
     * Inclusive lower bound of the interval.
     *
     * @return start date or value
     */
    public Object getFrom() {
        return from;
    }

    /**
     * Sets the inclusive lower bound.
     *
     * @param from start date or value
     */
    public void setFrom(Object from) {
        this.from = from;
    }

    /**
     * Inclusive upper bound of the interval.
     *
     * @return end date or value
     */
    public Object getTo() {
        return to;
    }

    /**
     * Sets the inclusive upper bound.
     *
     * @param to end date or value
     */
    public void setTo(Object to) {
        this.to = to;
    }
}
