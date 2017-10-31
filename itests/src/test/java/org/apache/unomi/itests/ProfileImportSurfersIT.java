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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by amidani on 09/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportSurfersIT extends BaseIT {

    @Inject
    @Filter("(configDiscriminator=IMPORT)")
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    @Inject
    protected ProfileService profileService;
    private Logger logger = LoggerFactory.getLogger(ProfileImportSurfersIT.class);


    @Before
    public void setUp() throws IOException, InterruptedException {

        /*** Create Missing Properties ***/
        PropertyType propertyType = new PropertyType(new Metadata("integration", "alive", "Alive", "Is the person alive?"));
        propertyType.setValueTypeId("boolean");
        propertyType.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyType.setTarget("profiles");

        profileService.setPropertyType(propertyType);

        /*** Surfers Test ***/
        ImportConfiguration importConfigSurfers = new ImportConfiguration();
        importConfigSurfers.setItemId("2-surfers-test");
        importConfigSurfers.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigSurfers.setMergingProperty("linkedInId");
        importConfigSurfers.setOverwriteExistingProfiles(true);
        importConfigSurfers.setColumnSeparator(";");
        importConfigSurfers.setHasHeader(true);
        importConfigSurfers.setHasDeleteColumn(true);

        Map mappingSurfers = new HashMap();
        mappingSurfers.put("linkedInId", 0);
        mappingSurfers.put("lastName", 1);
        mappingSurfers.put("email", 2);
        mappingSurfers.put("facebookId", 3);
        mappingSurfers.put("gender", 4);
        mappingSurfers.put("alive", 5);


        importConfigSurfers.getProperties().put("mapping", mappingSurfers);
        File importSurfersFile = new File("data/tmp/recurrent_import/");
        importConfigSurfers.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=2-surfers-test.csv&consumer.delay=10m&move=.done");
        importConfigSurfers.setActive(true);

        ImportConfiguration savedImportConfig = importConfigurationService.save(importConfigSurfers, true);

        //Wait for data to be processed
        Thread.sleep(10000);

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(URL + "/configUpdate/importConfigAdmin");

        String json = new ObjectMapper().writeValueAsString(savedImportConfig);
        StringEntity entity = new StringEntity(json);
        entity.setContentType(MediaType.APPLICATION_JSON);
        httpPut.setEntity(entity);

        HttpResponse response = httpclient.execute(httpPut);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        httpclient.close();

    }

    @Test
    public void testCheckAddedPropertiesSurfers() throws IOException, InterruptedException {

        //Wait for data to be processed
        Thread.sleep(1000);

        PropertyType propAlive = profileService.getPropertyType("alive");

        Assert.assertNotNull(propAlive);

    }

    @Test
    public void testImportSurfers() throws IOException, InterruptedException {

        //Wait for data to be processed
        Thread.sleep(10000);

        //Assert.assertEquals(37, profileService.getAllProfilesCount());

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
        ImportConfiguration importConfiguration = importConfigurationService.load("2-surfers-test");
        Assert.assertEquals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS, importConfiguration.getStatus());
        Assert.assertEquals(1, importConfiguration.getExecutions().size());

    }

}
