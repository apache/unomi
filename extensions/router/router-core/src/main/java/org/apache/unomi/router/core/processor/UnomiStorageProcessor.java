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
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ProfileImportService;
import org.apache.unomi.api.segments.SegmentsAndScores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * A Camel processor that handles the storage of imported profiles in the Unomi system.
 * This processor is responsible for managing the final stage of profile import, including
 * segment calculation and profile persistence.
 *
 * <p>The processor performs the following operations:
 * <ul>
 *   <li>Processes profiles marked for import</li>
 *   <li>Calculates segments and scores for non-deleted profiles</li>
 *   <li>Updates profile information with calculated segments</li>
 *   <li>Persists profiles in the Unomi storage system</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class UnomiStorageProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnomiStorageProcessor.class.getName());

    /** Service for handling profile import operations */
    private ProfileImportService profileImportService;

    /** Service for managing profile segments and scoring */
    private SegmentService segmentService;

    private ExecutionContextManager contextManager;

    private SecurityService securityService;

    /**
     * Processes the exchange by storing or updating the profile in Unomi's storage system.
     *
     * <p>This method:
     * <ul>
     *   <li>Extracts the ProfileToImport from the message body</li>
     *   <li>For non-delete operations, calculates and updates segments and scores</li>
     *   <li>Persists the profile using the ProfileImportService</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing the profile to process
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        ProfileToImport profileToImport = exchange.getIn().getBody(ProfileToImport.class);
        String tenantId = exchange.getIn().getHeader(RouterConstants.HEADER_TENANT_ID, String.class);

        if (tenantId == null) {
            LOGGER.error("No tenant ID found in exchange headers");
            throw new Exception("No tenant ID found in exchange headers");
        }

        securityService.setCurrentSubject(securityService.createSubject(tenantId, true));
        contextManager.executeAsTenant(tenantId, () -> {
            try {
                if (!profileToImport.isProfileToDelete()) {
                    SegmentsAndScores segmentsAndScoringForProfile = segmentService.getSegmentsAndScoresForProfile(profileToImport);
                    Set<String> segments = segmentsAndScoringForProfile.getSegments();
                    if (!segments.equals(profileToImport.getSegments())) {
                        profileToImport.setSegments(segments);
                    }
                    Map<String, Integer> scores = segmentsAndScoringForProfile.getScores();
                    if (!scores.equals(profileToImport.getScores())) {
                        profileToImport.setScores(scores);
                    }
                }

                profileImportService.saveMergeDeleteImportedProfile(profileToImport);
                exchange.getIn().setBody(profileToImport);
            } catch (Exception e) {
                LOGGER.error("Error processing profile import", e);
                throw new RuntimeException("Error processing profile import", e);
            }
            return null;
        });
    }

    /**
     * Sets the profile import service used for persisting profiles.
     *
     * @param profileImportService the service responsible for profile import operations
     */
    public void setProfileImportService(ProfileImportService profileImportService) {
        this.profileImportService = profileImportService;
    }

    /**
     * Sets the segment service used for calculating profile segments and scores.
     *
     * @param segmentService the service responsible for segment calculations
     */
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }
}
