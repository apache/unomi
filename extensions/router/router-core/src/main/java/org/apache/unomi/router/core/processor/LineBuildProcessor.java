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
import org.apache.unomi.api.Profile;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.services.ProfileExportService;

/**
 * A Camel processor that converts Unomi Profile objects into CSV lines for export.
 * This processor is responsible for transforming profile data into a formatted string
 * according to the export configuration specified in the exchange header.
 *
 * <p>The processor works in conjunction with the ProfileExportService to perform
 * the actual conversion of profile data to CSV format.</p>
 *
 * @since 1.0
 */
public class LineBuildProcessor implements Processor {

    private ProfileExportService profileExportService;

    /**
     * Constructs a new LineBuildProcessor with the specified ProfileExportService.
     *
     * @param profileExportService the service responsible for converting profiles to CSV format
     */
    public LineBuildProcessor(ProfileExportService profileExportService) {
        this.profileExportService = profileExportService;
    }

    /**
     * Processes the exchange by converting a Profile object into a CSV line.
     * 
     * <p>This method:
     * <ul>
     *   <li>Extracts the export configuration from the exchange header</li>
     *   <li>Gets the Profile object from the exchange body</li>
     *   <li>Converts the profile to a CSV line using the ProfileExportService</li>
     *   <li>Sets the resulting string as the new exchange body</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing the Profile to convert and export configuration
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        ExportConfiguration exportConfiguration = (ExportConfiguration) exchange.getIn().getHeader("exportConfig");
        Profile profile = exchange.getIn().getBody(Profile.class);

        String lineToWrite = profileExportService.convertProfileToCSVLine(profile, exportConfiguration );

        exchange.getIn().setBody(lineToWrite, String.class);
    }

}
