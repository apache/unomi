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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by amidani on 11/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportSurfersDeleteIT extends BaseIT {

    @Inject
    @Filter("(configDiscriminator=IMPORT)")
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    @Inject
    protected ProfileService profileService;
    private Logger logger = LoggerFactory.getLogger(ProfileImportSurfersDeleteIT.class);

    @Before
    public void setUp() throws IOException {

        Map mappingSurfers = new HashMap();
        mappingSurfers.put("linkedInId", 0);
        mappingSurfers.put("lastName", 1);
        mappingSurfers.put("email", 2);
        mappingSurfers.put("facebookId", 3);
        mappingSurfers.put("gender", 4);
        mappingSurfers.put("alive", 5);

        File importSurfersFile = new File("data/tmp/recurrent_import/");

        /*** Surfers Delete Test ***/
        ImportConfiguration importConfigSurfersDelete = new ImportConfiguration();
        importConfigSurfersDelete.setItemId("3-surfers-delete-test");
        importConfigSurfersDelete.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigSurfersDelete.setMergingProperty("linkedInId");
        importConfigSurfersDelete.setOverwriteExistingProfiles(true);
        importConfigSurfersDelete.setColumnSeparator(";");
        importConfigSurfersDelete.setHasHeader(true);
        importConfigSurfersDelete.setHasDeleteColumn(true);

        importConfigSurfersDelete.getProperties().put("mapping", mappingSurfers);

        importConfigSurfersDelete.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=3-surfers-delete-test.csv&consumer.delay=10m&move=.done");
        importConfigSurfersDelete.setActive(true);

        ImportConfiguration savedImportConfigDelete = importConfigurationService.save(importConfigSurfersDelete);

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(URL + "/configUpdate/importConfigAdmin");

        String jsonDelete = new ObjectMapper().writeValueAsString(savedImportConfigDelete);
        StringEntity entityDelete = new StringEntity(jsonDelete);
        entityDelete.setContentType(MediaType.APPLICATION_JSON);
        httpPut.setEntity(entityDelete);

        HttpResponse responseDelete = httpclient.execute(httpPut);
        assertThat(responseDelete.getStatusLine().getStatusCode(), equalTo(200));

        httpclient.close();

    }

    @Test
    public void testCheckImportConfigListSurfers() {

        List<ImportConfiguration> importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(3, importConfigurations.size());

    }

    @Test
    public void testImport3SurfersDelete() throws IOException, InterruptedException {

        //Wait for data to be processed
        Thread.sleep(5000);

        Assert.assertEquals(3, profileService.getAllProfilesCount());
        PartialList<Profile> jordyProfile = profileService.findProfilesByPropertyValue("properties.email", "jordy@smith.com", 0, 10, null);
        Assert.assertEquals(0, jordyProfile.getList().size());

    }
}
