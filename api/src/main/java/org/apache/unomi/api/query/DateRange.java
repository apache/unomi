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
 * Named inclusive date interval used in queries and aggregations.
 * Pairs a label ({@code key}) with {@code from} and {@code to} bounds so
 * conditions can match events or profiles inside a calendar window.
 */
public class DateRange implements Serializable {
    private String key;
    private Object from;
    private Object to;

    /**
     * Constructs a new {@link DateRange} instance.
     */
    public DateRange() {
    }

    /**
     * Retrieves the unique key associated with this date range.
     * @return The string key of the range.
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique identifier (key) for this date range.
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Retrieves the starting value (from) of the date range.
     * @return The object representing the start time or value.
     */
    public Object getFrom() {
        return from;
    }

    /**
     * Sets the starting value (from) for this date range.
     * @param from the starting value to set
     */
    public void setFrom(Object from) {
        this.from = from;
    }

    /**
     * Retrieves the ending value (to) of the date range.
     * @return The object representing the end time or value.
     */
    public Object getTo() {
        return to;
    }

    /**
     * Sets the ending value (to) for this date range.
     * @param to the ending value to set
     */
    public void setTo(Object to) {
        this.to = to;
    }
}
