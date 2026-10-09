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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

/**
 * UNOMI-1000: background work must reach every tenant, not only the system tenant. The test tenant the
 * suite runs in is a real tenant, so these tests fail when a task only sees {@code tenantId=system}.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class BackgroundTenantTasksIT extends BaseIT {

    private static final String RULE_ID = "unomi1000-statistics-rule";
    private static final String EVENT_TYPE = "unomi1000StatisticsEvent";
    private static final String SEGMENT_ID = "unomi1000-system-inactive-segment";
    private static final String IMPORT_CONFIG_ID = "unomi1000-shared-import";
    private static final String OTHER_TENANT_ID = "unomi1000-router-tenant";

    /**
     * The statistics of a tenant's rule are only persisted when the periodic statistics refresh runs
     * under that tenant.
     */
    @Test
    public void ruleStatisticsOfATenantRuleArePersisted() throws InterruptedException {
        String profileId = "unomi1000-statistics-profile";
        Profile profile = new Profile(profileId);
        profileService.save(profile);

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", EVENT_TYPE);
        Action action = new Action(definitionsService.getActionType("setPropertyAction"));
        action.setParameter("propertyName", "unomi1000Statistics");
        action.setParameter("propertyValue", "fired");
        Rule rule = new Rule(new Metadata("test-scope", RULE_ID, RULE_ID, "Counts its executions for UNOMI-1000"));
        rule.setCondition(condition);
        rule.setActions(Collections.singletonList(action));
        try {
            createAndWaitForRule(rule);
            rulesService.refreshRules();

            // The refresh runs every 10 seconds by default; keep the rule firing until its count is stored.
            RuleStatistics statistics = keepTrying("Statistics of a tenant rule were not persisted for the tenant",
                    () -> {
                        Event event = new Event(EVENT_TYPE, null, profile, null, null, profile, new Date());
                        event.setPersistent(false);
                        Assert.assertNotEquals(EventService.ERROR, eventService.send(event));
                        return persistenceService.load(RULE_ID, RuleStatistics.class);
                    },
                    stored -> stored != null && stored.getExecutionCount() > 0,
                    1000, 60);
            Assert.assertEquals("Statistics must be stored in the tenant owning the rule", TEST_TENANT_ID, statistics.getTenantId());
        } finally {
            rulesService.removeRule(RULE_ID);
            persistenceService.remove(RULE_ID, RuleStatistics.class);
            profileService.delete(profileId, false);
            waitForNullValue("Rule still present after deletion", () -> rulesService.getRule(RULE_ID), DEFAULT_TRYING_TIMEOUT,
                    DEFAULT_TRYING_TRIES);
        }
    }

    /**
     * A date-relative segment owned by the system tenant applies to the profiles of every tenant, so the
     * recalculation of a tenant has to re-evaluate it for that tenant's profiles.
     */
    @Test
    public void systemDateRelativeSegmentIsRecalculatedForTenantProfiles() throws InterruptedException {
        String profileId = "unomi1000-inactive-profile";
        Profile profile = new Profile(profileId);
        LocalDate twoMonthsAgo = LocalDate.now().minusDays(60);
        profile.setProperty("lastVisit", Date.from(twoMonthsAgo.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class);

        Condition inactiveForAMonth = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        inactiveForAMonth.setParameter("propertyName", "properties.lastVisit");
        inactiveForAMonth.setParameter("comparisonOperator", "lessThanOrEqualTo");
        inactiveForAMonth.setParameter("propertyValueDateExpr", "now-30d");
        Segment segment = new Segment(new Metadata(SEGMENT_ID));
        segment.setCondition(inactiveForAMonth);
        try {
            // Defining the segment under the system tenant only evaluates the system tenant's own profiles.
            executionContextManager.executeAsSystem(() -> segmentService.setSegmentDefinition(segment));
            keepTrying("The system segment is not visible from the tenant", () -> segmentService.getSegmentDefinition(SEGMENT_ID),
                    Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertFalse("The profile was saved directly, it cannot be in the segment yet",
                    profileService.load(profileId).getSegments().contains(SEGMENT_ID));

            // What the daily task runs under each tenant; events are disabled to keep the test free of races.
            segmentService.recalculatePastEventConditions(false);
            persistenceService.refreshIndex(Profile.class);

            keepTrying("The tenant profile did not enter the system tenant's date-relative segment",
                    () -> profileService.load(profileId),
                    updated -> updated != null && updated.getSegments().contains(SEGMENT_ID),
                    1000, 20);
        } finally {
            executionContextManager.executeAsSystem(() -> {
                segmentService.removeSegmentDefinition(SEGMENT_ID, false);
            });
            profileService.delete(profileId, false);
            waitForNullValue("Profile still present after deletion", () -> profileService.load(profileId), DEFAULT_TRYING_TIMEOUT,
                    DEFAULT_TRYING_TRIES);
        }
    }

    /**
     * Configuration ids are only unique within a tenant: two tenants using the same id each get their
     * own route, and removing one tenant's configuration leaves the other tenant's route running.
     */
    @Test
    public void sameImportConfigurationIdInTwoTenantsGetsTwoRoutes() throws InterruptedException {
        Tenant otherTenant = tenantService.createTenant(OTHER_TENANT_ID, Collections.emptyMap());
        String otherTenantId = otherTenant.getItemId();
        try {
            importConfigurationService.save(recurrentImport(TEST_TENANT_ID), true);
            executionContextManager.executeAsTenant(otherTenantId, () -> {
                importConfigurationService.save(recurrentImport(otherTenantId), true);
            });

            keepTrying("No route was started for the configuration of the test tenant",
                    () -> camelRouteExists(TEST_TENANT_ID, IMPORT_CONFIG_ID), exists -> exists, 1000, 30);
            keepTrying("No route was started for the configuration of the other tenant, which uses the same id",
                    () -> camelRouteExists(otherTenantId, IMPORT_CONFIG_ID), exists -> exists, 1000, 30);

            executionContextManager.executeAsTenant(otherTenantId, () -> importConfigurationService.delete(IMPORT_CONFIG_ID));

            keepTrying("The route of the deleted configuration is still there",
                    () -> camelRouteExists(otherTenantId, IMPORT_CONFIG_ID), exists -> !exists, 1000, 30);
            Assert.assertTrue("Deleting one tenant's configuration must not remove the route of another tenant's configuration",
                    camelRouteExists(TEST_TENANT_ID, IMPORT_CONFIG_ID));
        } finally {
            try {
                importConfigurationService.delete(IMPORT_CONFIG_ID);
                keepTrying("The route of the test tenant's configuration is still there after its deletion",
                        () -> camelRouteExists(TEST_TENANT_ID, IMPORT_CONFIG_ID), exists -> !exists, 1000, 30);
            } finally {
                tenantService.deleteTenant(otherTenantId);
            }
        }
    }

    /** A recurrent import polling the tenant's own directory for a file that never comes. */
    private ImportConfiguration recurrentImport(String tenantId) {
        File tenantImportDirectory = new File("data/tmp/recurrent_import/" + tenantId);
        tenantImportDirectory.mkdirs();

        ImportConfiguration configuration = new ImportConfiguration();
        configuration.setItemId(IMPORT_CONFIG_ID);
        configuration.setConfigType(RouterConstants.IMPORT_EXPORT_CONFIG_TYPE_RECURRENT);
        configuration.setMergingProperty("email");
        configuration.setOverwriteExistingProfiles(true);
        configuration.setColumnSeparator(";");
        configuration.setHasHeader(true);
        configuration.setHasDeleteColumn(false);
        configuration.getProperties().put("mapping", Collections.singletonMap("email", 0));
        configuration.getProperties().put("source",
                "file://" + tenantImportDirectory.getAbsolutePath() + "?fileName=unomi1000-never-delivered.csv&move=.done");
        configuration.setActive(true);
        return configuration;
    }
}
