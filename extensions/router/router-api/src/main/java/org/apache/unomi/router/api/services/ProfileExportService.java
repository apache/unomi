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
package org.apache.unomi.router.api.services;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.router.api.ExportConfiguration;

import java.util.Collection;

/**
 * Service interface for handling the export of profiles from Apache Unomi.
 * This service is responsible for extracting profiles based on segment criteria
 * and converting them into the appropriate export format (e.g., CSV).
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Extracting profiles based on segment criteria</li>
 *   <li>Converting profiles to export format</li>
 *   <li>Handling data formatting and transformation</li>
 *   <li>Managing export file generation</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Called by export route processors to handle profile extraction</li>
 *   <li>Used during scheduled export operations</li>
 *   <li>Integrated with Unomi's segmentation system</li>
 * </ul>
 * </p>
 *
 * <p>Implementation considerations:
 * <ul>
 *   <li>Must handle large data sets efficiently</li>
 *   <li>Should implement proper error handling</li>
 *   <li>Must respect profile property formatting</li>
 *   <li>Should handle multi-valued properties</li>
 * </ul>
 * </p>
 *
 * @see Profile
 * @see ExportConfiguration
 * @see PropertyType
 * @since 1.0
 */
public interface ProfileExportService {

    /**
     * Extracts profiles belonging to a specified segment and formats them for export.
     * This method handles the bulk export operation, including:
     * - Querying profiles based on segment criteria
     * - Formatting profiles according to export configuration
     * - Generating the export content
     *
     * @param exportConfiguration the configuration specifying export parameters and format
     * @return a String containing the formatted export data
     */
    String extractProfilesBySegment(ExportConfiguration exportConfiguration);

    /**
     * Converts a single profile to a CSV line format according to the export configuration.
     * This method handles the formatting of individual profiles, including:
     * - Property selection and ordering
     * - Value formatting
     * - Multi-value handling
     * - Line separator management
     *
     * @param profile the profile to convert
     * @param exportConfiguration the configuration specifying the export format
     * @return a String containing the CSV-formatted profile data
     */
    String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration);

}
