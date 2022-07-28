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

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by amidani on 03/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportBasicIT extends BaseIT {
    private Logger logger = LoggerFactory.getLogger(ProfileImportBasicIT.class);

    @Test
    public void testImportBasic() throws IOException, InterruptedException {
        /*** Basic Test ***/
        ImportConfiguration importConfiguration = new ImportConfiguration();
        String itemId = "1-basic-test";
        importConfiguration.setItemId(itemId);
        importConfiguration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_ONESHOT);
        importConfiguration.setMergingProperty("email");
        importConfiguration.setOverwriteExistingProfiles(true);

        Map mapping = new HashMap();
        mapping.put("email", 0);
        mapping.put("firstName", 1);
        mapping.put("lastName", 2);
        mapping.put("city", 3);

        importConfiguration.getProperties().put("mapping", mapping);
        importConfiguration.setActive(true);

        logger.info("Save import config oneshot with ID : {}.", itemId);
        importConfigurationService.save(importConfiguration, false);

        // Wait for the config to be processed
        Thread.sleep(5000);

        logger.info("Check import config oneshot with ID : {}.", itemId);
        List<ImportConfiguration> importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(1, importConfigurations.size());

        // Move the file to the import folder so the import can start
        File basicFile = new File("data/tmp/1-basic-test.csv");
        Files.copy(basicFile.toPath(), new File("data/tmp/unomi_oneshot_import_configs/1-basic-test.csv").toPath(), StandardCopyOption.REPLACE_EXISTING);

        //Wait for the csv to be processed
        PartialList<Profile> profiles = keepTrying("Failed waiting for basic import test to complete", ()->profileService.findProfilesByPropertyValue("properties.city", "oneShotImportCity", 0, 10, null), (p)->p.getTotalSize() == 3, 1000, 200);
        Assert.assertEquals(3, profiles.getList().size());

        checkProfiles(1);
        checkProfiles(2);
        checkProfiles(3);

        importConfigurationService.delete(itemId);
    }

    private void checkProfiles(int profileNumber) {
        String propertyValue = "basic" + profileNumber + "@test.com";
        PartialList<Profile> profiles = profileService.findProfilesByPropertyValue("properties.email", propertyValue, 0, 10, null);
        Assert.assertNotNull(profiles.get(0));
        Assert.assertEquals("Basic" + profileNumber, profiles.get(0).getProperty("firstName"));
        Assert.assertEquals("User" + profileNumber, profiles.get(0).getProperty("lastName"));
    }
}
