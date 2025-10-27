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

/**
 * Aggregation that buckets documents by unique terms of a field.
 * Optionally supports partitioning to split large cardinalities across multiple requests.
 */
public class TermsAggregate extends BaseAggregate{
    /**
     * Zero-based partition index when using partitioned terms aggregation; {@code -1} means disabled.
     */
    private int partition = -1;
    /**
     * Total number of partitions when using partitioned terms aggregation; {@code -1} means disabled.
     */
    private int numPartitions = -1;


    public TermsAggregate(String field) {
        super(field);
    }

    public TermsAggregate(String field, int partition, int numPartitions) {
        super(field);
        this.partition = partition;
        this.numPartitions = numPartitions;
    }

    /**
     * Returns the zero-based partition index, or {@code -1} if partitioning is disabled.
     */
    public int getPartition() {
        return partition;
    }

    /**
     * Returns the total number of partitions, or {@code -1} if partitioning is disabled.
     */
    public int getNumPartitions() {
        return numPartitions;
    }
}
