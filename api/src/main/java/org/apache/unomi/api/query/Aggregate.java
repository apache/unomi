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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specification for an aggregation used by {@link AggregateQuery}.
 * <p>
 * The {@link #type} identifies the aggregation strategy: {@code date}, {@code dateRange},
 * {@code numericRange}, {@code ipRange}, or {@code null} (terms aggregation on distinct values).
 * Type-specific configuration is supplied through {@link #parameters} and the corresponding range lists.
 *
 * @see AggregateQuery
 */
public class Aggregate implements Serializable {
    private String type;
    private String property;
    private Map<String, Object> parameters = new HashMap<>();
    private List<DateRange> dateRanges = new ArrayList<>();
    private List<NumericRange> numericRanges = new ArrayList<>();
    private List<IpRange> ipRanges = new ArrayList<>();


    /**
     * Instantiates a new Aggregate.
     */
    public Aggregate() {
    }

    /**
     * Retrieves the aggregation type.
     * <p>
     * Supported values are {@code date}, {@code dateRange}, {@code numericRange}, and {@code ipRange}.
     * When {@code null}, a terms aggregation is used on distinct property values.
     *
     * @return the aggregation type, or {@code null} for a terms aggregation
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the aggregation type.
     * <p>
     * Supported values are {@code date}, {@code dateRange}, {@code numericRange}, and {@code ipRange}.
     * When {@code null}, a terms aggregation is used on distinct property values.
     *
     * @param type the aggregation type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Retrieves the aggregation parameters.
     * <p>
     * For {@code date} aggregations, expected keys are {@code interval} and {@code format}.
     * For {@code dateRange} aggregations, the {@code format} key defines the date format.
     *
     * @return the aggregation parameters
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Sets the aggregation parameters.
     * <p>
     * For {@code date} aggregations, expected keys are {@code interval} and {@code format}.
     * For {@code dateRange} aggregations, the {@code format} key defines the date format.
     *
     * @param parameters the aggregation parameters
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * Retrieves the property to aggregate on.
     *
     * @return the property to aggregate on
     */
    public String getProperty() {
        return property;
    }

    /**
     * Sets the property to aggregate on.
     *
     * @param property the property name
     */
    public void setProperty(String property) {
        this.property = property;
    }

    /**
     * Retrieves the numeric ranges used by {@code numericRange} aggregations.
     *
     * @return the numeric ranges
     */
    public List<NumericRange> getNumericRanges() {
        return numericRanges;
    }

    /**
     * Retrieves the date ranges used by {@code dateRange} aggregations.
     *
     * @return the date ranges
     */
    public List<DateRange> getDateRanges() {
        return dateRanges;
    }

    /**
     * Sets the date ranges used by {@code dateRange} aggregations.
     *
     * @param dateRanges the date ranges
     */
    public void setDateRanges(List<DateRange> dateRanges) {
        this.dateRanges = dateRanges;
    }

    /**
     * Retrieves the IP ranges used by {@code ipRange} aggregations.
     *
     * @return the IP ranges
     */
    public List<IpRange> ipRanges() {
        return ipRanges;
    }

    /**
     * Sets the IP ranges used by {@code ipRange} aggregations.
     *
     * @param ipRanges the IP ranges
     */
    public void setIpRanges(List<IpRange> ipRanges) {
        this.ipRanges = ipRanges;
    }
}
