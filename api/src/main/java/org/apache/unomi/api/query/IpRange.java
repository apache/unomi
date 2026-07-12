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
 * Named inclusive IP address interval for geo or network filtering.
 * Used in segment conditions to match visitors whose IP falls between
 * the configured lower and upper bounds.
 */
public class IpRange implements Serializable {
    private String key;
    private String from;
    private String to;

    /**
     * Constructs an empty {@link IpRange} instance.
     */
    public IpRange() {
    }

    /**
     * Retrieves the unique key associated with this IP range.
     * @return The string key identifying the range.
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the unique identifier (key) for this IP range.
     * @param key The key to assign to the range.
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Retrieves the starting IP address string of the range.
     * @return The 'from' boundary of the IP range.
     */
    public String getFrom() {
        return from;
    }

    /**
     * Sets the starting IP address string for this range.
     * @param from The start boundary of the IP range.
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Retrieves the ending IP address string of the range.
     * @return The 'to' boundary of the IP range.
     */
    public String getTo() {
        return to;
    }

    /**
     * Sets the ending IP address string for this range.
     * @param to The end boundary of the IP range.
     */
    public void setTo(String to) {
        this.to = to;
    }
}
