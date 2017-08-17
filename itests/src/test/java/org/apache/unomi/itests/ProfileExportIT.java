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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.router.api.ExportConfiguration;
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
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by amidani on 14/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileExportIT extends BaseIT {

    @Inject
    @Filter("(configDiscriminator=EXPORT)")
    protected ImportExportConfigurationService<ExportConfiguration> exportConfigurationService;

    @Inject
    protected SegmentService segmentService;

    @Inject
    protected DefinitionsService definitionsService;

    private Logger logger = LoggerFactory.getLogger(ProfileExportIT.class);

    File exportDir;


    @Before
    public void setUp() throws IOException, InterruptedException {

        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", "properties.twitterId");
        condition.setParameter("propertyValue", "3");
        condition.setParameter("comparisonOperator", "greaterThanOrEqualTo");

        Segment segment = new Segment(new Metadata("integration", "exportItSeg", "Export IT Segment", "Export IT Segment"));
        segment.setItemId("exportItSeg");
        segment.setCondition(condition);
        segment.setScope("integration");

        segmentService.setSegmentDefinition(segment);

        Thread.sleep(2000);

        /*** Export Test ***/
        ExportConfiguration exportConfiguration = new ExportConfiguration();
        exportConfiguration.setItemId("export-test");
        exportConfiguration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        exportConfiguration.setColumnSeparator(";");
        exportConfiguration.setMultiValueDelimiter("()");
        exportConfiguration.setMultiValueSeparator(";");

        Map mapping = new HashMap();
        mapping.put("0", "lastName");
        mapping.put("1", "email");
        mapping.put("2", "movieGenres");

        exportConfiguration.getProperties().put("mapping", mapping);
        exportConfiguration.getProperties().put("segment", "exportItSeg");
        exportConfiguration.getProperties().put("period", "10m");
        exportDir = new File("data/tmp/recurrent_export/");
        exportConfiguration.getProperties().put("destination", "file://" + exportDir.getAbsolutePath() + "?fileName=profiles-actors-export.csv");
        exportConfiguration.setActive(true);

        ExportConfiguration savedExportConfig = exportConfigurationService.save(exportConfiguration);

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(URL + "/configUpdate/exportConfigAdmin");

        String json = new ObjectMapper().writeValueAsString(savedExportConfig);
        StringEntity entity = new StringEntity(json);
        entity.setContentType(MediaType.APPLICATION_JSON);
        httpPut.setEntity(entity);

        HttpResponse response = httpclient.execute(httpPut);
        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));

        httpclient.close();

    }

    @Test
    public void testCheckSegment() {
        Segment segment = segmentService.getSegmentDefinition("exportItSeg");

        Assert.assertNotNull(segment);
        Assert.assertEquals("Export IT Segment", segment.getMetadata().getName());
    }

    @Test
    public void testCheckExportConfigList() {

        List<ExportConfiguration> exportConfigurations = exportConfigurationService.getAll();
        Assert.assertEquals(1, exportConfigurations.size());

    }

    @Test
    public void testExport() throws InterruptedException {

        Thread.sleep(5000);

        File exportResult = new File(exportDir+"/profiles-actors-export.csv");
        logger.info("PATH : {}", exportResult.getAbsolutePath());
        Assert.assertTrue(exportResult.exists());
    }

}
