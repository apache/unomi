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
package org.apache.unomi.router.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.router.api.ExportConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileExportServiceImplTest {

    private ProfileExportServiceImpl profileExportService;

    @BeforeEach
    void setUp() {
        profileExportService = new ProfileExportServiceImpl();
    }

    @Test
    void extractProfilesBySegment_missingSegment_throwsIllegalArgumentException() {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setProperty("mapping", Map.of("0", "firstName"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> profileExportService.extractProfilesBySegment(configuration));

        assertEquals("Export segment is required", exception.getMessage());
    }

    @Test
    void extractProfilesBySegment_blankSegment_throwsIllegalArgumentException() {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setProperty("segment", "   ");
        configuration.setProperty("mapping", Map.of("0", "firstName"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> profileExportService.extractProfilesBySegment(configuration));

        assertEquals("Export segment is required", exception.getMessage());
    }

    @Test
    void extractProfilesBySegment_missingMapping_throwsIllegalArgumentException() {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setProperty("segment", "frequent-buyers");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> profileExportService.extractProfilesBySegment(configuration));

        assertEquals("Export mapping is required", exception.getMessage());
    }

    @Test
    void convertProfileToCSVLine_missingMapping_throwsIllegalArgumentException() {
        ExportConfiguration configuration = new ExportConfiguration();
        Profile profile = new Profile("profile-1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> profileExportService.convertProfileToCSVLine(profile, configuration, Collections.emptyList()));

        assertEquals("Export mapping is required", exception.getMessage());
    }

    @Test
    void convertProfileToCSVLine_nullPropertyValue_writesEmptyField() {
        ExportConfiguration configuration = new ExportConfiguration();
        configuration.setColumnSeparator(",");
        Map<String, String> mapping = new HashMap<>();
        mapping.put("0", "firstName");
        configuration.setProperty("mapping", mapping);

        Profile profile = new Profile("profile-1");
        PropertyType propertyType = new PropertyType();
        propertyType.setMetadata(new Metadata("firstName"));

        String line = profileExportService.convertProfileToCSVLine(profile, configuration,
            Collections.singletonList(propertyType));

        assertTrue(line.isEmpty());
    }
}
