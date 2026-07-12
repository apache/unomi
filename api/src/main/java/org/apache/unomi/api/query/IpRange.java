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
 * Named inclusive IP range used in geo or network segment conditions.
 * The {@code from} and {@code to} strings define lower and upper bounds checked
 * when evaluating whether a visitor's IP address belongs to the range.
 */
public class IpRange implements Serializable {
    private String key;
    private String from;
    private String to;

    /**
     * Creates an empty IP range.
     */
    public IpRange() {
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
     * Inclusive lower IP bound.
     *
     * @return start IP address
     */
    public String getFrom() {
        return from;
    }

    /**
     * Sets the inclusive lower IP bound.
     *
     * @param from start IP address
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * Inclusive upper IP bound.
     *
     * @return end IP address
     */
    public String getTo() {
        return to;
    }

    /**
     * Sets the inclusive upper IP bound.
     *
     * @param to end IP address
     */
    public void setTo(String to) {
        this.to = to;
    }
}
