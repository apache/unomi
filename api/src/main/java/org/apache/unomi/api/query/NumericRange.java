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
 * Named inclusive numeric interval for query filters.
 * Stores a label plus lower and upper bounds so conditions can match
 * numeric profile or event properties in a range.
 */
public class NumericRange implements Serializable {
    private String key;
    private Double from;
    private Double to;

    /**
     * Constructs a new NumericRange object.
     */
    public NumericRange() {
    }

    /**
     * Retrieves the unique key associated with this numeric range.
     * @return The string key identifying the range.
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique identifier (key) for this numeric range.
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Retrieves the starting value of the numeric range.
     * @return The lower bound (inclusive start) of the
     * range, or null if not set.
     */
    public Double getFrom() {
        return from;
    }

    /**
     * Sets the starting value for this numeric range.
     * @param from the inclusive starting value of the range
     */
    public void setFrom(Double from) {
        this.from = from;
    }

    /**
     * Retrieves the ending value of the numeric range.
     * @return The upper bound (inclusive end) of the range, or null if not set.
     */
    public Double getTo() {
        return to;
    }

    /**
     * Sets the ending value for this numeric range.
     * @param to the inclusive ending value of the range
     */
    public void setTo(Double to) {
        this.to = to;
    }
}
