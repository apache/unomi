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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.unomi.api.segments.SegmentsAndScores;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.router.api.ProfileToImport;
import org.apache.unomi.router.api.services.ProfileImportService;

import java.util.Map;
import java.util.Set;

/**
 * Created by amidani on 29/12/2016.
 */
public class UnomiStorageProcessor implements Processor {

    private ProfileImportService profileImportService;
    private SegmentService segmentService;

    @Override
    public void process(Exchange exchange)
            throws Exception {
        if (exchange.getIn() != null) {
            Message message = exchange.getIn();

            ProfileToImport profileToImport = (ProfileToImport) message.getBody();

            if(!profileToImport.isProfileToDelete()) {
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
        }
    }

    public void setProfileImportService(ProfileImportService profileImportService) {
        this.profileImportService = profileImportService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }
}
