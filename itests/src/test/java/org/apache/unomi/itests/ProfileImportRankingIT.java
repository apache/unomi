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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by amidani on 09/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportRankingIT extends BaseIT {

    @Test
    public void testImportRanking() throws InterruptedException {

        importConfigurationService.getRouterCamelContext().setTracing(true);

        /*** Create Missing Properties ***/
        PropertyType propertyTypeUciId = new PropertyType(new Metadata("integration", "uciId", "UCI ID", "UCI ID"));
        propertyTypeUciId.setValueTypeId("string");
        propertyTypeUciId.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyTypeUciId.setTarget("profiles");

        profileService.setPropertyType(propertyTypeUciId);

        PropertyType propertyTypeRank = new PropertyType(new Metadata("integration", "rank", "Rank", "Rank"));
        propertyTypeRank.setValueTypeId("integer");
        propertyTypeRank.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyTypeRank.setTarget("profiles");

        profileService.setPropertyType(propertyTypeRank);

        PropertyType propUciId = keepTrying("Failed waiting for property type 'uciId'", () -> profileService.getPropertyType("uciId"),
                Objects::nonNull, 1000, 100);

        PropertyType propRankId = keepTrying("Failed waiting for property type 'rank'", () -> profileService.getPropertyType("rank"),
                Objects::nonNull, 1000, 100);

        /*** Surfers Test ***/
        String itemId = "5-ranking-test";
        ImportConfiguration importConfigRanking = new ImportConfiguration();
        importConfigRanking.setItemId(itemId);
        importConfigRanking.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigRanking.setMergingProperty("rank");
        importConfigRanking.setOverwriteExistingProfiles(true);
        importConfigRanking.setColumnSeparator(";");
        importConfigRanking.setHasHeader(true);
        importConfigRanking.setHasDeleteColumn(false);

        Map<String, Integer> mappingRanking = new HashMap<>();
        mappingRanking.put("rank", 0);
        mappingRanking.put("uciId", 1);
        mappingRanking.put("lastName", 2);
        mappingRanking.put("nationality", 3);
        mappingRanking.put("age", 4);
        mappingRanking.put("city", 5);

        importConfigRanking.getProperties().put("mapping", mappingRanking);
        File importSurfersFile = new File("data/tmp/recurrent_import/");
        importConfigRanking.getProperties().put("source",
                "file://" + importSurfersFile.getAbsolutePath() + "?fileName=5-ranking-test.csv&consumer.delay=10m&move=.done");
        importConfigRanking.setActive(true);

        importConfigurationService.save(importConfigRanking, true);

        //Wait for data to be processed
        keepTrying("Failed waiting for ranking import to complete",
                () -> profileService.findProfilesByPropertyValue("properties.city", "rankingCity", 0, 50, null),
                (p) -> p.getTotalSize() == 25, 1000, 200);

        List<ImportConfiguration> importConfigurations = keepTrying("Failed waiting for import configurations list with 1 item",
                () -> importConfigurationService.getAll(), (list) -> Objects.nonNull(list) && list.size() == 1, 1000, 100);

        PartialList<Profile> gregProfileList = profileService.findProfilesByPropertyValue("properties.uciId", "10004451371", 0, 10, null);
        Assert.assertEquals(1, gregProfileList.getList().size());
        Profile gregProfile = gregProfileList.get(0);
        Assert.assertNotNull(gregProfile);
        Assert.assertEquals(1, gregProfile.getProperty("rank"));
        Assert.assertEquals("VAN AVERMAET Greg", gregProfile.getProperty("lastName"));
        Assert.assertEquals("BELGIUM", gregProfile.getProperty("nationality"));
        Assert.assertEquals(32, gregProfile.getProperty("age"));

        importConfigurationService.delete(itemId);
    }
}
