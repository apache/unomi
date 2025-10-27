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

import org.apache.unomi.api.query.NumericRange;

import java.util.List;

/**
 * Aggregation that buckets numeric values of a field into the provided ranges.
 */
public class NumericRangeAggregate extends BaseAggregate{
    private List<NumericRange> ranges;

    /**
     * Creates a numeric range aggregation.
     *
     * @param field  the field to aggregate on
     * @param ranges the list of numeric ranges
     */
    public NumericRangeAggregate(String field, List<NumericRange> ranges) {
        super(field);
        this.ranges = ranges;
    }

    /**
     * Returns the configured numeric ranges.
     */
    public List<NumericRange> getRanges() {
        return ranges;
    }

    /**
     * Sets the numeric ranges to use for bucketing.
     */
    public void setRanges(List<NumericRange> ranges) {
        this.ranges = ranges;
    }
}
