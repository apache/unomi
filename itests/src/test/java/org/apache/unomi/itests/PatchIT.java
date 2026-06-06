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
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PatchIT extends BaseIT {
    private Logger LOGGER = LoggerFactory.getLogger(PatchIT.class);

    @Test
    public void testPatch() throws IOException, InterruptedException {
        PropertyType company = profileService.getPropertyType("company");

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch1.json"), Patch.class);
            PropertyType newCompany = (PropertyType) patchService.patch(patch);

            Assert.assertEquals("foo", newCompany.getDefaultValue());

            profileService.refresh();

            newCompany = keepTrying("Failed waiting for patched property type",
                    () -> profileService.getPropertyType("company"),
                    pt -> pt != null && "foo".equals(pt.getDefaultValue()),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals("foo", newCompany.getDefaultValue());
        } finally {
            profileService.setPropertyType(company);
        }
    }

    @Test
    public void testOverride() throws IOException, InterruptedException {
        PropertyType gender = profileService.getPropertyType("gender");

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch2.json"), Patch.class);
            PropertyType newGender = (PropertyType) patchService.patch(patch);

            Assert.assertEquals("foo", newGender.getDefaultValue());

            profileService.refresh();

            newGender = keepTrying("Failed waiting for patched property type",
                    () -> profileService.getPropertyType("gender"),
                    pt -> pt != null && "foo".equals(pt.getDefaultValue()),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals("foo", newGender.getDefaultValue());
        } finally {
            profileService.setPropertyType(gender);
        }
    }

    @Test
    public void testRemove() throws IOException, InterruptedException {
        PropertyType income = profileService.getPropertyType("income");

        try {
            // We need to execute as system to remove a system property type
            executionContextManager.executeAsSystem(() -> {
                Patch patch = null;
                try {
                    patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch3.json"), Patch.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Object patchResult = patchService.patch(patch);
                LOGGER.info("testRemove: patch applied, result={}", patchResult);

                profileService.refresh();

                // Poll with refresh on every attempt — nudges the unified cache each cycle.
                // Logs each result to diagnose whether the type reappears from bundle resources.
                try {
                    keepTrying("Failed waiting for property type removal",
                            () -> {
                                profileService.refresh();
                                PropertyType current = profileService.getPropertyType("income");
                                LOGGER.info("testRemove: poll — income={}", current == null
                                        ? "null (REMOVED OK)"
                                        : "still present, defaultValue=" + current.getDefaultValue());
                                return current;
                            },
                            value -> value == null,
                            DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES * 2);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while trying to wait for property removal", e);
                }
            });
        } finally {
            profileService.setPropertyType(income);
        }
    }

    @Test
    public void testPatchOnConditionType() throws IOException, InterruptedException {
        ConditionType formCondition = definitionsService.getConditionType("formEventCondition");
        Assert.assertTrue(formCondition.getMetadata().getSystemTags().contains("profileTags"));

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch4.json"), Patch.class);

            patchService.patch(patch);

            definitionsService.refresh();

            ConditionType newFormCondition = keepTrying("Failed waiting for patched condition type",
                    () -> definitionsService.getConditionType("formEventCondition"),
                    ct -> ct != null && !ct.getMetadata().getSystemTags().contains("profileTags"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertFalse(newFormCondition.getMetadata().getSystemTags().contains("profileTags"));
        } finally {
            definitionsService.setConditionType(formCondition);
        }
    }

    @Test
    public void testPatchOnActionType() throws IOException, InterruptedException {
        ActionType mailAction = definitionsService.getActionType("sendMailAction");
        Assert.assertNotNull("sendMailAction should exist", mailAction);
        Assert.assertNotNull("ActionType metadata should not be null", mailAction.getMetadata());
        Assert.assertNotNull("ActionType systemTags should not be null", mailAction.getMetadata().getSystemTags());
        Assert.assertTrue(mailAction.getMetadata().getSystemTags().contains("availableToEndUser"));

        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(bundleContext.getBundle().getResource("patch5.json"), Patch.class);

            patchService.patch(patch);

            definitionsService.refresh();

            ActionType newMailAction = keepTrying("Failed waiting for patched action type",
                    () -> definitionsService.getActionType("sendMailAction"),
                    at -> at != null && !at.getMetadata().getSystemTags().contains("availableToEndUser"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertFalse(newMailAction.getMetadata().getSystemTags().contains("availableToEndUser"));
        } finally {
            definitionsService.setActionType(mailAction);
        }
    }
}
