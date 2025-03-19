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

import org.apache.unomi.router.api.ProfileToImport;

import java.lang.reflect.InvocationTargetException;

/**
 * Service interface for handling the import of individual profiles into Apache Unomi.
 * This service is responsible for the actual processing and storage of imported profile data,
 * including merging with existing profiles or creating new ones as needed.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Processing individual profile imports</li>
 *   <li>Merging imported data with existing profiles</li>
 *   <li>Handling profile creation for new imports</li>
 *   <li>Managing profile deletion when specified</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Called by import route processors to handle individual profile data</li>
 *   <li>Used during batch import operations</li>
 *   <li>Integrated with Unomi's profile management system</li>
 * </ul>
 * </p>
 *
 * <p>Implementation considerations:
 * <ul>
 *   <li>Must handle profile merging strategies</li>
 *   <li>Should implement proper error handling</li>
 *   <li>Must maintain data consistency</li>
 *   <li>Should handle property type conversions</li>
 * </ul>
 * </p>
 *
 * @see ProfileToImport
 * @see org.apache.unomi.api.Profile
 * @since 1.0
 */
public interface ProfileImportService {

    /**
     * Processes a profile for import, handling the save, merge, or delete operation as specified.
     * This method is the core functionality for profile import processing, determining whether to:
     * - Create a new profile
     * - Merge with an existing profile
     * - Delete an existing profile
     *
     * @param profileToImport the profile data to be imported, containing all necessary information
     *                       for the import operation including the operation type
     * @return true if the operation was successful, false otherwise
     * @throws InvocationTargetException if there is an error during property mapping
     * @throws IllegalAccessException if there is an error accessing profile properties
     */
    boolean saveMergeDeleteImportedProfile(ProfileToImport profileToImport) throws InvocationTargetException, IllegalAccessException;
}
