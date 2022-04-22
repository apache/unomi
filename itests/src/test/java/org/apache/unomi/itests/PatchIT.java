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

import org.apache.unomi.api.Patch;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.PatchService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PatchIT extends BaseIT {
    private Logger logger = LoggerFactory.getLogger(PatchIT.class);

    @Inject
    @Filter(timeout = 600000)
    protected PatchService patchService;

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Inject
    protected BundleContext bundleContext;

    @Test
    public void testPatch() throws IOException {
        PropertyType company = profileService.getPropertyType("company");

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch1.json"), Patch.class);
            PropertyType newCompany = (PropertyType) patchService.patch(patch);

            Assert.assertEquals("foo", newCompany.getDefaultValue());

            profileService.refresh();

            newCompany = profileService.getPropertyType("company");
            Assert.assertEquals("foo", newCompany.getDefaultValue());
        } finally {
            profileService.setPropertyType(company);
        }
    }

    @Test
    public void testOverride() throws IOException {
        PropertyType gender = profileService.getPropertyType("gender");

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch2.json"), Patch.class);
            PropertyType newGender = (PropertyType) patchService.patch(patch);

            Assert.assertEquals("foo", newGender.getDefaultValue());

            profileService.refresh();

            newGender = profileService.getPropertyType("gender");
            Assert.assertEquals("foo", newGender.getDefaultValue());
        } finally {
            profileService.setPropertyType(gender);
        }
    }

    @Test
    public void testRemove() throws IOException, InterruptedException {
        PropertyType income = profileService.getPropertyType("income");

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch3.json"), Patch.class);

            patchService.patch(patch);

            profileService.refresh();

            PropertyType newIncome = profileService.getPropertyType("income");
            Assert.assertNull(newIncome);
        } finally {
            profileService.setPropertyType(income);
        }
    }

    @Test
    public void testPatchOnConditionType() throws IOException, InterruptedException {
        ConditionType formCondition = definitionsService.getConditionType("formEventCondition");
        Assert.assertTrue(formCondition.getMetadata().getSystemTags().contains("usableInPastEventCondition"));

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch4.json"), Patch.class);

            patchService.patch(patch);

            definitionsService.refresh();

            ConditionType newFormCondition = definitionsService.getConditionType("formEventCondition");
            Assert.assertFalse(newFormCondition.getMetadata().getSystemTags().contains("usableInPastEventCondition"));
        } finally {
            definitionsService.setConditionType(formCondition);
        }
    }

    @Test
    public void testPatchOnActionType() throws IOException, InterruptedException {
        ActionType mailAction = definitionsService.getActionType("sendMailAction");
        Assert.assertTrue(mailAction.getMetadata().getSystemTags().contains("availableToEndUser"));

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch5.json"), Patch.class);

            patchService.patch(patch);

            definitionsService.refresh();

            ActionType newMailAction = definitionsService.getActionType("sendMailAction");
            Assert.assertFalse(newMailAction.getMetadata().getSystemTags().contains("availableToEndUser"));
        } finally {
            definitionsService.setActionType(mailAction);
        }
    }
}
