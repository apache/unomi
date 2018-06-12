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

/**
 * Created by amidani on 09/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportRankingIT extends BaseIT {

    @Inject
    @Filter("(configDiscriminator=IMPORT)")
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    @Inject
    protected ProfileService profileService;

    @Test
    public void testImportRanking() throws InterruptedException {

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

        PropertyType propUciId = profileService.getPropertyType("uciId");
        Assert.assertNotNull(propUciId);

        PropertyType propRankId = profileService.getPropertyType("rank");
        Assert.assertNotNull(propRankId);


        /*** Surfers Test ***/
        ImportConfiguration importConfigRanking = new ImportConfiguration();
        importConfigRanking.setItemId("5-ranking-test");
        importConfigRanking.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigRanking.setMergingProperty("rank");
        importConfigRanking.setOverwriteExistingProfiles(true);
        importConfigRanking.setColumnSeparator(";");
        importConfigRanking.setHasHeader(true);
        importConfigRanking.setHasDeleteColumn(false);

        Map mappingRanking = new HashMap();
        mappingRanking.put("rank", 0);
        mappingRanking.put("uciId", 1);
        mappingRanking.put("lastName", 2);
        mappingRanking.put("nationality", 3);
        mappingRanking.put("age", 4);

        importConfigRanking.getProperties().put("mapping", mappingRanking);
        File importSurfersFile = new File("data/tmp/recurrent_import/");
        importConfigRanking.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=5-ranking-test.csv&consumer.delay=10m&move=.done");
        importConfigRanking.setActive(true);

        importConfigurationService.save(importConfigRanking, true);


        //Wait for data to be processed
        Thread.sleep(10000);

        List<ImportConfiguration> importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(5, importConfigurations.size());

        PartialList<Profile> gregProfile = profileService.findProfilesByPropertyValue("properties.uciId", "10004451371", 0, 10, null);
        Assert.assertEquals(1, gregProfile.getList().size());
        Assert.assertNotNull(gregProfile.get(0));
        Assert.assertEquals(1, gregProfile.get(0).getProperty("rank"));
        Assert.assertEquals("VAN AVERMAET Greg", gregProfile.get(0).getProperty("lastName"));
        Assert.assertEquals("BELGIUM", gregProfile.get(0).getProperty("nationality"));
        Assert.assertEquals(32, gregProfile.get(0).getProperty("age"));


    }

}
