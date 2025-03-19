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
package org.apache.unomi.router.core.bean;

import org.apache.unomi.api.Profile;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.List;

/**
 * A bean that handles the collection of profiles based on segment criteria.
 * This class provides functionality to extract profiles from Unomi's persistence
 * layer based on segment membership.
 *
 * <p>Features:
 * <ul>
 *   <li>Segment-based profile extraction</li>
 *   <li>Integration with Unomi's persistence service</li>
 *   <li>Batch profile retrieval capabilities</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class CollectProfileBean {

    /** Service for accessing Unomi's persistence layer */
    private PersistenceService persistenceService;

    /**
     * Extracts profiles that belong to a specific segment.
     * This method queries Unomi's persistence layer to retrieve all profiles
     * that are members of the specified segment.
     *
     * <p>Note: As per UNOMI-759, this method currently loads all profiles into RAM.
     * This behavior will be optimized in future versions.</p>
     *
     * @param segment the segment identifier to filter profiles by
     * @return a list of Profile objects that belong to the specified segment
     */
    public List<Profile> extractProfileBySegment(String segment) {
        // TODO: UNOMI-759 avoid loading all profiles in RAM here
        return persistenceService.query("segments", segment,null, Profile.class);
    }

    /**
     * Sets the persistence service used for profile queries.
     *
     * @param persistenceService the Unomi persistence service to use
     */
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }
}
