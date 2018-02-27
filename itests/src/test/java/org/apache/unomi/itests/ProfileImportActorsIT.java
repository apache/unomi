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
import java.util.*;

/**
 * Created by amidani on 14/08/2017.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileImportActorsIT extends BaseIT {

    @Inject
    @Filter("(configDiscriminator=IMPORT)")
    protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    @Inject
    protected ProfileService profileService;

    @Test
    public void testImportActors() throws InterruptedException {

        /*** Create Missing Properties ***/
        PropertyType propertyTypeTwitterId = new PropertyType(new Metadata("integration", "twitterId", "Twitter ID", "Twitter ID"));
        propertyTypeTwitterId.setValueTypeId("integer");
        propertyTypeTwitterId.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyTypeTwitterId.setTarget("profiles");

        PropertyType propertyTypeActorsGenres = new PropertyType(new Metadata("integration", "movieGenres", "Movie Genres", "Movie Genres"));
        propertyTypeActorsGenres.setValueTypeId("string");
        propertyTypeActorsGenres.setMultivalued(true);
        propertyTypeActorsGenres.getMetadata().setSystemTags(Collections.singleton("basicProfileProperties"));
        propertyTypeActorsGenres.setTarget("profiles");

        profileService.setPropertyType(propertyTypeTwitterId);
        profileService.setPropertyType(propertyTypeActorsGenres);

        PropertyType propTwitterId = profileService.getPropertyType("twitterId");
        Assert.assertNotNull(propTwitterId);

        PropertyType propActorsGenre = profileService.getPropertyType("movieGenres");
        Assert.assertNotNull(propActorsGenre);


        /*** Actors Test ***/
        ImportConfiguration importConfigActors = new ImportConfiguration();
        importConfigActors.setItemId("6-actors-test");
        importConfigActors.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        importConfigActors.setMergingProperty("twitterId");
        importConfigActors.setOverwriteExistingProfiles(true);
        importConfigActors.setColumnSeparator(";");
        importConfigActors.setMultiValueDelimiter("[]");
        importConfigActors.setMultiValueSeparator(";");
        importConfigActors.setHasHeader(true);
        importConfigActors.setHasDeleteColumn(false);

        Map mappingActors = new HashMap();
        mappingActors.put("twitterId", 0);
        mappingActors.put("lastName", 1);
        mappingActors.put("email", 2);
        mappingActors.put("movieGenres", 3);

        importConfigActors.getProperties().put("mapping", mappingActors);
        File importSurfersFile = new File("data/tmp/recurrent_import/");
        importConfigActors.getProperties().put("source", "file://" + importSurfersFile.getAbsolutePath() + "?fileName=6-actors-test.csv&consumer.delay=10m&move=.done");
        importConfigActors.setActive(true);

        importConfigurationService.save(importConfigActors, true);

        //Wait for data to be processed
        Thread.sleep(10000);

        List<ImportConfiguration> importConfigurations = importConfigurationService.getAll();
        Assert.assertEquals(6, importConfigurations.size());

        PartialList<Profile> jeanneProfile = profileService.findProfilesByPropertyValue("properties.twitterId", "4", 0, 10, null);
        Assert.assertEquals(1, jeanneProfile.getList().size());
        Assert.assertNotNull(jeanneProfile.get(0));
        Assert.assertEquals("Jeanne; D'arc", jeanneProfile.get(0).getProperty("lastName"));
        Assert.assertEquals("jean@darc.com", jeanneProfile.get(0).getProperty("email"));
        Assert.assertArrayEquals(new String[]{}, ((List) jeanneProfile.get(0).getProperty("movieGenres")).toArray());

        PartialList<Profile> rockProfile = profileService.findProfilesByPropertyValue("properties.twitterId", "6", 0, 10, null);
        Assert.assertEquals(1, rockProfile.getList().size());
        Assert.assertNotNull(rockProfile.get(0));
        Assert.assertEquals("The Rock", rockProfile.get(0).getProperty("lastName"));
        Assert.assertEquals("the.rock@gmail.com", rockProfile.get(0).getProperty("email"));
        Assert.assertEquals(Arrays.asList("Adventure", "Action", "Romance", "Comedy"), rockProfile.get(0).getProperty("movieGenres"));

    }

}
