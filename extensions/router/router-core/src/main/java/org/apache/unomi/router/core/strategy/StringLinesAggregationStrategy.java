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
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterUtils;

/**
 * An implementation of Camel's AggregationStrategy that combines multiple text lines into a single string
 * for export purposes. This strategy is specifically designed to work with the Unomi Router's export functionality,
 * where multiple data lines need to be aggregated into a single export file.
 * 
 * <p>The strategy maintains the following behavior:
 * <ul>
 *   <li>For the first message (when oldExchange is null), it simply returns the new exchange</li>
 *   <li>For subsequent messages, it appends the new content to the existing content using the configured line separator</li>
 * </ul>
 * </p>
 * 
 * <p>The line separator used for aggregation is obtained from the ExportConfiguration object
 * stored in the exchange header under the key "exportConfig".</p>
 * 
 * @since 1.0
 */
public class StringLinesAggregationStrategy implements AggregationStrategy {

    /**
     * Aggregates two exchanges by combining their body content with appropriate line separation.
     * 
     * <p>This method implements the core aggregation logic where:
     * <ul>
     *   <li>The new exchange's body is extracted as a String</li>
     *   <li>The line separator is obtained from the export configuration in the exchange header</li>
     *   <li>If there's an old exchange, the new content is appended to it with the line separator</li>
     *   <li>If there's no old exchange, the new exchange is returned as is</li>
     * </ul>
     * </p>
     *
     * @param oldExchange the previous exchange being aggregated (may be null on first invocation)
     * @param newExchange the current exchange being aggregated (contains the new line to append)
     * @return the aggregated exchange containing the combined content
     */
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Object newBody = newExchange.getIn().getBody(String.class);
        String lineSeparator = newExchange.getIn().getHeader("exportConfig", ExportConfiguration.class).getLineSeparator();
        if (oldExchange != null) {
            StringBuilder fileContent = new StringBuilder();
            fileContent.append(oldExchange.getIn().getBody(String.class));
            fileContent.append(RouterUtils.getCharFromLineSeparator(lineSeparator));
            fileContent.append(newBody);

            oldExchange.getIn().setBody(fileContent);
            return oldExchange;
        } else {
            return newExchange;
        }
    }
}
