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
 * limitations under the License
 */
package org.apache.unomi.itests;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Created by amidani on 14/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileExportIT extends BaseIT {
    private Logger LOGGER = LoggerFactory.getLogger(ProfileExportIT.class);

    @Test
    public void testExport() throws InterruptedException {
        Date timestamp = new Date();

        Set<String> segments = new TreeSet<>();
        segments.add("exportItSeg");

        Profile profile1 = new Profile(UUID.randomUUID().toString());
        profile1.setProperty("firstVisit", timestamp);
        profile1.setProperty("firstName", "Pablo");
        profile1.setProperty("lastName", "Esco");
        profile1.setProperty("city", "exportCity");
        profile1.setSegments(segments);
        profileService.save(profile1);

        Profile profile2 = new Profile(UUID.randomUUID().toString());
        profile2.setProperty("firstVisit", timestamp);
        profile2.setProperty("firstName", "Amado");
        profile2.setProperty("lastName", "Carri");
        profile2.setProperty("city", "exportCity");
        profile2.setSegments(segments);
        profileService.save(profile2);

        Profile profile3 = new Profile(UUID.randomUUID().toString());
        profile3.setProperty("firstVisit", timestamp);
        profile3.setProperty("firstName", "Joaquin");
        profile3.setProperty("lastName", "Guz");
        profile3.setProperty("city", "exportCity");
        profile3.setSegments(segments);
        profileService.save(profile3);

        keepTrying("Failed waiting for the creation of the profiles for the export test",
                () -> profileService.findProfilesByPropertyValue("segments", "exportItSeg", 0, 10, null), (p) -> p.getTotalSize() == 3,
                1000, 100);

        /*** Export Test ***/
        String itemId = "export-test";
        ExportConfiguration exportConfiguration = new ExportConfiguration();
        exportConfiguration.setItemId(itemId);
        exportConfiguration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        exportConfiguration.setColumnSeparator(";");
        exportConfiguration.setMultiValueDelimiter("()");
        exportConfiguration.setMultiValueSeparator(";");

        Map<String, String> mapping = new HashMap<>();
        mapping.put("0", "firstName");
        mapping.put("1", "lastName");
        mapping.put("2", "city");

        exportConfiguration.getProperties().put("mapping", mapping);
        exportConfiguration.getProperties().put("segment", "exportItSeg");
        exportConfiguration.getProperties().put("period", "1m");
        File exportDir = new File("data/tmp/");
        exportConfiguration.getProperties().put("destination", "file://" + exportDir.getAbsolutePath() + "?fileName=profiles-export.csv");
        exportConfiguration.setActive(true);

        exportConfigurationService.save(exportConfiguration, true);

        final File exportResult = new File("data/tmp/profiles-export.csv");
        keepTrying("Failed waiting for export file to be created", () -> exportResult, File::exists, 1000, 100);

        LOGGER.info("PATH : {}", exportResult.getAbsolutePath());
        Assert.assertTrue(exportResult.exists());

        List<ExportConfiguration> exportConfigurations = exportConfigurationService.getAll();
        Assert.assertEquals(1, exportConfigurations.size());

        exportConfigurationService.delete(itemId);
    }
}
