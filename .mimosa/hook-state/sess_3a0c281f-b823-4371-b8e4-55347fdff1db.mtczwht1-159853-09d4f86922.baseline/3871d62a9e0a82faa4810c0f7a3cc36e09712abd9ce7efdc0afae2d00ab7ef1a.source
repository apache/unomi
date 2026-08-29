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
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Called by export route processors to handle profile extraction</li>
 *   <li>Used during scheduled export operations</li>
 *   <li>Integrated with Unomi's segmentation system</li>
 * </ul>
 *
 * <p>Implementation considerations:
 * <ul>
 *   <li>Must handle large data sets efficiently</li>
 *   <li>Should implement proper error handling</li>
 *   <li>Must respect profile property formatting</li>
 *   <li>Should handle multi-valued properties</li>
 * </ul>
 *
 * @see Profile
 * @see ExportConfiguration
 * @see PropertyType
 * @since 1.0
 */
public interface ProfileExportService {

    /**
     * Extracts profiles belonging to a specified segment and formats them for export.
     * Implementations typically query profiles by segment, build CSV content (including line separators
     * between rows), append an execution record to the configuration, and persist the updated configuration.
     *
     * @param exportConfiguration the configuration specifying export parameters and format
     * @return CSV (or configured delimited) content for the extracted profiles
     */
    String extractProfilesBySegment(ExportConfiguration exportConfiguration);

    /**
     * Converts a single profile to one delimited row according to the export configuration mapping.
     * Does not append line separators; callers or export routes add separators between rows.
     *
     * @param profile the profile to convert
     * @param exportConfiguration the configuration specifying the export format
     * @return one row of delimited profile data (no trailing line separator)
     */
    String convertProfileToCSVLine(Profile profile, ExportConfiguration exportConfiguration);

}
