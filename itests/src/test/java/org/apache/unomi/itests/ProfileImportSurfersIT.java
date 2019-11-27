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
import org.apache.unomi.router.api.RouterUtils;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 09/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportSurfersIT extends BaseIT {
    private Logger logger = LoggerFactory.getLogger(ProfileImportSurfersIT.class);

    @Inject @Filter(value="(configDiscriminator=IMPORT)", timeout = 600000)
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;
    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Test
    public void testImportSurfers() throws IOException, InterruptedException {

        /*** Create Missing Properties ***/
        PropertyType propertyType = new PropertyType(new Metadata("integration", "alive", "Alive", "Is the person alive?"));
        propertyType.setValueTypeId("boolean");
        propertyType.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyType.setTarget("profiles");

        profileService.setPropertyType(propertyType);

        PropertyType propAlive = profileService.getPropertyType("alive");

        Assert.assertNotNull("Alive property should not be null", propAlive);

        propAlive = RouterUtils.getPropertyTypeById(profileService.getTargetPropertyTypes("profiles"), "alive");

        Assert.assertNotNull("Lookup of alive property through profiles target has failed !", propAlive);

        /*** Surfers Test ***/
        ImportConfiguration importConfigSurfers = new ImportConfiguration();
        String itemId1 = "2-surfers-test";
        importConfigSurfers.setItemId(itemId1);
        importConfigSurfers.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigSurfers.setMergingProperty("linkedInId");
        importConfigSurfers.setOverwriteExistingProfiles(true);
        importConfigSurfers.setColumnSeparator(";");
        importConfigSurfers.setHasHeader(true);
        importConfigSurfers.setHasDeleteColumn(true);

        Map<String, Integer> mappingSurfers = new HashMap<>();
        mappingSurfers.put("linkedInId", 0);
        mappingSurfers.put("lastName", 1);
        mappingSurfers.put("email", 2);
        mappingSurfers.put("facebookId", 3);
        mappingSurfers.put("gender", 4);
        mappingSurfers.put("alive", 5);
        mappingSurfers.put("city", 6);

        importConfigSurfers.getProperties().put("mapping", mappingSurfers);
        File importSurfersFile = new File("data/tmp/recurrent_import/");
        importConfigSurfers.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=2-surfers-test.csv&consumer.delay=10m&move=.done");
        importConfigSurfers.setActive(true);

        importConfigurationService.save(importConfigSurfers, true);

        logger.info("ProfileImportSurfersIT setup successfully.");

        //Wait for data to be processed
        keepTrying(()->profileService.findProfilesByPropertyValue("properties.city", "surfersCity", 0, 50, null), (p)->p.getTotalSize() == 34, 1000, 40);

        List<ImportConfiguration> importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(1, importConfigurations.size());

        //Profile not to delete
        PartialList<Profile> jordyProfile = profileService.findProfilesByPropertyValue("properties.email", "jordy@smith.com", 0, 10, null);
        Assert.assertEquals(1, jordyProfile.getList().size());
        Assert.assertNotNull(jordyProfile.get(0));
        Assert.assertEquals("1", jordyProfile.get(0).getProperty("linkedInId"));
        Assert.assertEquals("Jordy Smith", jordyProfile.get(0).getProperty("lastName"));
        Assert.assertEquals("999", jordyProfile.get(0).getProperty("facebookId"));
        Assert.assertEquals("male", jordyProfile.get(0).getProperty("gender"));
        Assert.assertTrue((Boolean) jordyProfile.get(0).getProperty("alive"));

        //Profile to delete
        PartialList<Profile> paulineProfile = profileService.findProfilesByPropertyValue("properties.lastName", "Pauline Ado", 0, 10, null);
        Assert.assertEquals(0, paulineProfile.getList().size());

        //Check import config status
        ImportConfiguration importConfiguration = importConfigurationService.load(itemId1);
        Assert.assertEquals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS, importConfiguration.getStatus());
        Assert.assertEquals(1, importConfiguration.getExecutions().size());
        importConfigurationService.delete(itemId1);

        /*** Surfers Test OVERWRITE ***/
        ImportConfiguration importConfigSurfersOverwrite = new ImportConfiguration();
        String itemId2 = "3-surfers-overwrite-test";
        importConfigSurfersOverwrite.setItemId(itemId2);
        importConfigSurfersOverwrite.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigSurfersOverwrite.setMergingProperty("linkedInId");
        importConfigSurfersOverwrite.setOverwriteExistingProfiles(true);
        importConfigSurfersOverwrite.setColumnSeparator(";");
        importConfigSurfersOverwrite.setHasHeader(true);
        importConfigSurfersOverwrite.setHasDeleteColumn(true);

        importConfigSurfersOverwrite.getProperties().put("mapping", mappingSurfers);
        importConfigSurfersOverwrite.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=3-surfers-overwrite-test.csv&consumer.delay=10m&move=.done");
        importConfigSurfersOverwrite.setActive(true);

        importConfigurationService.save(importConfigSurfersOverwrite, true);

        logger.info("ProfileImportSurfersOverwriteIT setup successfully.");

        //Wait for data to be processed
        keepTrying(()->profileService.findProfilesByPropertyValue("properties.city", "surfersCity", 0, 50, null), (p)->p.getTotalSize() == 36, 1000, 40);

        importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(1, importConfigurations.size());

        //Profile not to delete
        PartialList<Profile> aliveProfiles = profileService.findProfilesByPropertyValue("properties.alive", "true", 0, 50, null);
        PartialList<Profile> deadProfiles = profileService.findProfilesByPropertyValue("properties.alive", "false", 0, 50, null);

        Assert.assertEquals(0, aliveProfiles.getList().size());
        Assert.assertEquals(36, deadProfiles.getList().size());

        //Profile to delete = false, was to delete
        PartialList<Profile> paulineProfileOverwrite = profileService.findProfilesByPropertyValue("properties.lastName", "Pauline Ado", 0, 10, null);
        Assert.assertEquals(1, paulineProfileOverwrite.getList().size());
        importConfigurationService.delete(itemId2);

        /*** Surfers Delete Test ***/

        ImportConfiguration importConfigSurfersDelete = new ImportConfiguration();
        String itemId3 = "4-surfers-delete-test";
        importConfigSurfersDelete.setItemId(itemId3);
        importConfigSurfersDelete.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigSurfersDelete.setMergingProperty("linkedInId");
        importConfigSurfersDelete.setOverwriteExistingProfiles(true);
        importConfigSurfersDelete.setColumnSeparator(";");
        importConfigSurfersDelete.setHasHeader(true);
        importConfigSurfersDelete.setHasDeleteColumn(true);

        importConfigSurfersDelete.getProperties().put("mapping", mappingSurfers);

        importConfigSurfersDelete.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=4-surfers-delete-test.csv&consumer.delay=10m&move=.done");
        importConfigSurfersDelete.setActive(true);

        importConfigurationService.save(importConfigSurfersDelete, true);

        logger.info("ProfileImportSurfersDeleteIT setup successfully.");

        //Wait for data to be processed
        keepTrying(()->profileService.findProfilesByPropertyValue("properties.city", "surfersCity", 0, 50, null), (p)->p.getTotalSize() == 0, 1000, 40);

        importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(1, importConfigurations.size());

        PartialList<Profile> jordyProfileDelete = profileService.findProfilesByPropertyValue("properties.email", "jordy@smith.com", 0, 10, null);
        Assert.assertEquals(0, jordyProfileDelete.getList().size());

        importConfigurationService.delete(itemId3);
    }
}
