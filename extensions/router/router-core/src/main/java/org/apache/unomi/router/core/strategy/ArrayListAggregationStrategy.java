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
package org.apache.unomi.router.core.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import java.util.ArrayList;

/**
 * An implementation of Camel's AggregationStrategy that aggregates exchange bodies into an ArrayList.
 * This strategy is useful when you need to collect multiple messages into a single list for batch processing
 * or grouped operations within the Unomi Router.
 *
 * <p>The strategy maintains the following behavior:
 * <ul>
 *   <li>For the first message (when oldExchange is null), it creates a new ArrayList and adds the message body to it</li>
 *   <li>For subsequent messages, it adds the new message body to the existing ArrayList</li>
 * </ul>
 * </p>
 *
 * <p>The ArrayList is maintained in the exchange body, allowing for easy access to all aggregated items
 * once the aggregation is complete.</p>
 *
 * @since 1.0
 */
public class ArrayListAggregationStrategy implements AggregationStrategy {

    /**
     * Aggregates exchange messages by collecting their bodies into an ArrayList.
     *
     * <p>This method implements the core aggregation logic where:
     * <ul>
     *   <li>The new exchange's body is extracted as is (maintaining its original type)</li>
     *   <li>If this is the first message, a new ArrayList is created to store the messages</li>
     *   <li>The new body is added to the ArrayList</li>
     *   <li>The ArrayList is maintained in the exchange body for subsequent aggregations</li>
     * </ul>
     * </p>
     *
     * @param oldExchange the previous exchange being aggregated (may be null on first invocation)
     * @param newExchange the current exchange being aggregated (contains the new item to add to the list)
     * @return the aggregated exchange containing the ArrayList of all aggregated items
     */
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Object newBody = newExchange.getIn().getBody();
        ArrayList<Object> list = null;
        if (oldExchange == null) {
            list = new ArrayList<Object>();
            list.add(newBody);
            newExchange.getIn().setBody(list);
            return newExchange;
        } else {
            list = oldExchange.getIn().getBody(ArrayList.class);
            list.add(newBody);
            return oldExchange;
        }
    }
}
