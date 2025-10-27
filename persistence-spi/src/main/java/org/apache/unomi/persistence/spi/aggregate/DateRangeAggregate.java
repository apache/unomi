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

package org.apache.unomi.persistence.spi.aggregate;

import org.apache.unomi.api.query.DateRange;

import java.util.List;

/**
 * Aggregation that buckets date/time values of a field into the provided ranges,
 * using an optional date {@code format}.
 */
public class DateRangeAggregate extends BaseAggregate{

    private String format;
    private List<DateRange> dateRanges;

    /**
     * Creates a date range aggregation.
     *
     * @param field      the field to aggregate on
     * @param format     optional date format understood by the persistence layer
     * @param dateRanges the list of date ranges
     */
    public DateRangeAggregate(String field, String format, List<DateRange> dateRanges) {
        super(field);
        this.format = format;
        this.dateRanges = dateRanges;
    }

    /**
     * Returns the configured date ranges.
     */
    public List<DateRange> getDateRanges() {
        return dateRanges;
    }

    /**
     * Sets the date ranges to use for bucketing.
     */
    public void setDateRanges(List<DateRange> dateRanges) {
        this.dateRanges = dateRanges;
    }

    /**
     * Returns the date format, if any.
     */
    public String getFormat() {
        return format;
    }

    /**
     * Sets the date format.
     */
    public void setFormat(String format) {
        this.format = format;
    }
}
