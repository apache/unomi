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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.unomi.router.api.ImportLineError;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.exceptions.BadProfileDataFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel processor that handles failures during the line splitting process of data import.
 * This processor is responsible for creating structured error reports when lines fail to process,
 * providing detailed information about the nature of the failure and the problematic data.
 *
 * <p>The handler processes different types of exceptions:
 * <ul>
 *   <li>BadProfileDataFormatException - for data format related errors</li>
 *   <li>General exceptions - capturing the root cause message</li>
 * </ul>
 * </p>
 *
 * <p>For each failure, it creates an ImportLineError object containing:
 * <ul>
 *   <li>The error code or message</li>
 *   <li>The content of the failed line</li>
 *   <li>The line number in the source file</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class LineSplitFailureHandler implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LineSplitFailureHandler.class.getName());

    /**
     * Processes failures that occur during line splitting and creates structured error reports.
     * 
     * <p>This method:
     * <ul>
     *   <li>Logs the failure details including the route ID and exception</li>
     *   <li>Creates an ImportLineError object with detailed error information</li>
     *   <li>Extracts the appropriate error message based on the exception type</li>
     *   <li>Sets the failure information in the exchange for further processing</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing the failed message and exception details
     * @throws Exception if an error occurs during failure handling
     */
    public void process(Exchange exchange) throws Exception {
        LOGGER.error("Route: {}, Error: {}", exchange.getProperty(Exchange.FAILURE_ROUTE_ID), exchange.getProperty(Exchange.EXCEPTION_CAUGHT));
        ImportLineError importLineError = new ImportLineError();
        if (exchange.getProperty(Exchange.EXCEPTION_CAUGHT) instanceof BadProfileDataFormatException) {
            importLineError.setErrorCode(((BadProfileDataFormatException) exchange.getProperty(Exchange.EXCEPTION_CAUGHT)).getCause().getMessage());
        } else if (exchange.getProperty(Exchange.EXCEPTION_CAUGHT) instanceof Throwable) {
            Throwable rootCause = (Throwable) exchange.getProperty(Exchange.EXCEPTION_CAUGHT);
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            importLineError.setErrorCode(rootCause.getMessage());
        } else {
            importLineError.setErrorCode(exchange.getProperty(Exchange.EXCEPTION_CAUGHT).toString());
        }
        importLineError.setLineContent(exchange.getIn().getBody(String.class));
        importLineError.setLineNb(((Integer) exchange.getProperty("CamelSplitIndex") + 1));
        exchange.getIn().setHeader(RouterConstants.HEADER_FAILED_MESSAGE, Boolean.TRUE);
        exchange.getIn().setBody(importLineError, ImportLineError.class);
    }
}
