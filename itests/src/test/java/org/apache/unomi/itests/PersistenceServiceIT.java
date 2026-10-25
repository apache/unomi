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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.persistence.spi.aggregate.DateAggregate;
import org.apache.unomi.persistence.spi.aggregate.DateRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.NumericRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Integration tests for {@link org.apache.unomi.persistence.spi.PersistenceService} against the live
 * search backend (Elasticsearch or OpenSearch).
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PersistenceServiceIT extends BaseIT {

    private static final String AGE_PROPERTY = "properties.age";

    private final List<String> profileIds = new ArrayList<>();
    private final List<String> eventIds = new ArrayList<>();

    @After
    public void tearDown() throws InterruptedException {
        for (String eventId : eventIds) {
            persistenceService.remove(eventId, Event.class);
        }
        eventIds.clear();
        for (String profileId : profileIds) {
            persistenceService.remove(profileId, Profile.class);
        }
        profileIds.clear();
        refreshPersistence(Event.class, Profile.class);
    }

    @Test
    public void testRangeQueryReturnsProfilesInNumericRange() throws InterruptedException {
        saveProfileWithAge("range-query-it-low", 10);
        saveProfileWithAge("range-query-it-match-a", 20);
        saveProfileWithAge("range-query-it-match-b", 30);
        saveProfileWithAge("range-query-it-match-c", 40);
        saveProfileWithAge("range-query-it-high", 50);

        refreshPersistence(Profile.class);

        // Both bounds are inclusive, so age 40 (the "to" value) must be included while age 50 is excluded.
        PartialList<Profile> results = keepTrying(
                "Range query should return profiles with age between 20 and 40",
                () -> persistenceService.rangeQuery(AGE_PROPERTY, "20", "40", AGE_PROPERTY + ":asc", Profile.class, 0, -1),
                r -> r != null && r.getList().size() == 3,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        Assert.assertEquals(3, results.getList().size());
        Assert.assertEquals(20, results.getList().get(0).getProperty("age"));
        Assert.assertEquals(30, results.getList().get(1).getProperty("age"));
        Assert.assertEquals(40, results.getList().get(2).getProperty("age"));
    }

    @Test
    public void testRangeQuerySupportsPagination() throws InterruptedException {
        for (int age = 1; age <= 5; age++) {
            saveProfileWithAge("range-query-it-page-" + age, age);
        }

        refreshPersistence(Profile.class);

        PartialList<Profile> firstPage = keepTrying(
                "First range query page should be available",
                () -> persistenceService.rangeQuery(AGE_PROPERTY, "1", "6", AGE_PROPERTY + ":asc", Profile.class, 0, 2),
                r -> r != null && r.getList().size() == 2 && r.getTotalSize() == 5,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        Assert.assertEquals(2, firstPage.getList().size());
        Assert.assertEquals(5, firstPage.getTotalSize());
        Assert.assertEquals(1, firstPage.getList().get(0).getProperty("age"));
        Assert.assertEquals(2, firstPage.getList().get(1).getProperty("age"));

        PartialList<Profile> lastPage = keepTrying(
                "Last range query page should be available",
                () -> persistenceService.rangeQuery(AGE_PROPERTY, "1", "6", AGE_PROPERTY + ":asc", Profile.class, 4, 2),
                r -> r != null && r.getList().size() == 1 && r.getTotalSize() == 5,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        Assert.assertEquals(1, lastPage.getList().size());
        Assert.assertEquals(5, lastPage.getTotalSize());
        Assert.assertEquals(5, lastPage.getList().get(0).getProperty("age"));
    }

    @Test
    public void testPurgeByScopeKeepsOtherScopesAndProfiles() throws InterruptedException {
        String suffix = UUID.randomUUID().toString();
        String dropScope = "purge-drop-" + suffix;
        String keepScope = "purge-keep-" + suffix;
        Profile profile = saveProfile("purge-profile-" + suffix);
        Event dropEvent = saveViewEvent("purge-drop-event-" + suffix, profile, dropScope);
        Event keepEvent = saveViewEvent("purge-keep-event-" + suffix, profile, keepScope);
        refreshPersistence(Event.class, Profile.class);

        persistenceService.purge(dropScope);
        refreshPersistence(Event.class, Profile.class);

        Assert.assertNull("Events of the purged scope should be gone", persistenceService.load(dropEvent.getItemId(), Event.class));
        Assert.assertNotNull("Events of another scope should stay", persistenceService.load(keepEvent.getItemId(), Event.class));
        Assert.assertNotNull("Profiles have no scope and must stay", persistenceService.load(profile.getItemId(), Profile.class));
    }

    @Test
    public void testPurgeByScopeDoesNotRemoveAnotherTenantsEvents() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String scope = "purge-shared-scope-" + suffix;
        // Items of the other tenant are not tracked for tearDown, which runs as the current tenant and would not find them
        String otherProfileId = "purge-other-profile-" + suffix;
        String otherEventId = "purge-other-event-" + suffix;
        Tenant otherTenant = tenantService.createTenant("purge-other-" + suffix.substring(0, 8), Collections.emptyMap());
        try {
            Event localEvent = saveViewEvent("purge-local-event-" + suffix, saveProfile("purge-local-profile-" + suffix), scope);
            executionContextManager.executeAsTenant(otherTenant.getItemId(), () -> {
                Profile otherProfile = new Profile(otherProfileId);
                persistenceService.save(otherProfile);
                persistenceService.save(new Event(otherEventId, "view", null, otherProfile, scope, null, null, new Date()));
            });
            refreshPersistence(Event.class, Profile.class);

            persistenceService.purge(scope);
            refreshPersistence(Event.class, Profile.class);

            Assert.assertNull("Current tenant events in the scope should be gone",
                    persistenceService.load(localEvent.getItemId(), Event.class));
            executionContextManager.executeAsTenant(otherTenant.getItemId(), () -> {
                Assert.assertNotNull("Other tenant events in a scope with the same name should stay",
                        persistenceService.load(otherEventId, Event.class));
            });
        } finally {
            try {
                executionContextManager.executeAsTenant(otherTenant.getItemId(), () -> {
                    persistenceService.remove(otherEventId, Event.class);
                    persistenceService.remove(otherProfileId, Profile.class);
                });
            } finally {
                tenantService.deleteTenant(otherTenant.getItemId());
            }
        }
    }

    @Test
    public void testPurgeByScopeRemovesMoreThanOnePageOfItems() throws InterruptedException {
        String suffix = UUID.randomUUID().toString();
        String scope = "purge-many-" + suffix;
        Profile profile = saveProfile("purge-many-profile-" + suffix);
        // purge reads matching items 100 at a time
        int eventCount = 250;
        for (int i = 0; i < eventCount; i++) {
            saveViewEvent("purge-many-event-" + i + "-" + suffix, profile, scope);
        }
        refreshPersistence(Event.class, Profile.class);

        persistenceService.purge(scope);
        refreshPersistence(Event.class, Profile.class);

        for (int i = 0; i < eventCount; i++) {
            Assert.assertNull("Event " + i + " of the purged scope should be gone",
                    persistenceService.load("purge-many-event-" + i + "-" + suffix, Event.class));
        }
    }

    @Test
    public void testPurgeByScopeMatchesMixedCaseAndAccentedScope() throws InterruptedException {
        String suffix = UUID.randomUUID().toString();
        String scope = "Purge-Mixed-Scopé-" + suffix;
        Event event = saveViewEvent("purge-mixed-event-" + suffix, saveProfile("purge-mixed-profile-" + suffix), scope);
        refreshPersistence(Event.class, Profile.class);

        persistenceService.purge(scope);
        refreshPersistence(Event.class, Profile.class);

        Assert.assertNull("Events of the purged scope should be gone", persistenceService.load(event.getItemId(), Event.class));
    }

    @Test
    public void testPurgeByScopeRejectsBlankScope() {
        try {
            persistenceService.purge(" ");
            Assert.fail("A blank scope should be refused");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("required"));
        }
    }

    /**
     * UNOMI-1014: a terms aggregate with a condition must return buckets again (non-optimized and optimized).
     */
    @Test
    public void testAggregateWithConditionReturnsBuckets() throws InterruptedException {
        String suffix = UUID.randomUUID().toString();
        saveProfileWithFirstName("agg-alice-" + suffix, "Alice");
        saveProfileWithFirstName("agg-alice-2-" + suffix, "Alice");
        saveProfileWithFirstName("agg-bob-" + suffix, "Bob");
        refreshPersistence(Profile.class);

        Condition matchAll = matchAllCondition();
        TermsAggregate byFirstName = new TermsAggregate("properties.firstName");

        Map<String, Long> withCondition = keepTrying(
                "Aggregate with a condition should return first-name buckets",
                () -> persistenceService.aggregateQuery(matchAll, byFirstName, Profile.ITEM_TYPE),
                r -> r != null && Long.valueOf(2L).equals(r.get("Alice")) && Long.valueOf(1L).equals(r.get("Bob")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Assert.assertEquals(Long.valueOf(2L), withCondition.get("Alice"));
        Assert.assertEquals(Long.valueOf(1L), withCondition.get("Bob"));
        long profileCount = persistenceService.getAllItemsCount(Profile.ITEM_TYPE);
        Assert.assertEquals("_all must match getAllItemsCount for the current tenant",
                Long.valueOf(profileCount), withCondition.get("_all"));
        Assert.assertEquals("With match-all, _filtered must match getAllItemsCount for the current tenant",
                Long.valueOf(profileCount), withCondition.get("_filtered"));

        Map<String, Long> optimized = keepTrying(
                "Optimized aggregate with a condition should return first-name buckets",
                () -> persistenceService.aggregateWithOptimizedQuery(matchAll, byFirstName, Profile.ITEM_TYPE),
                r -> r != null && Long.valueOf(2L).equals(r.get("Alice")) && Long.valueOf(1L).equals(r.get("Bob")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Assert.assertEquals(Long.valueOf(2L), optimized.get("Alice"));
        Assert.assertEquals(Long.valueOf(1L), optimized.get("Bob"));
    }

    /**
     * Aggregate and metric queries must only see the current tenant's documents.
     */
    @Test
    public void testAggregateAndMetricsStayInTheCurrentTenant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String localAlice = "agg-tenant-local-alice-" + suffix;
        String otherAlice = "agg-tenant-other-alice-" + suffix;
        String otherBob = "agg-tenant-other-bob-" + suffix;
        Tenant otherTenant = tenantService.createTenant("agg-other-" + suffix, Collections.emptyMap());
        try {
            saveProfileWithFirstName(localAlice, "Alice");
            Profile local = persistenceService.load(localAlice, Profile.class);
            local.setProperty("age", 7);
            persistenceService.save(local);
            executionContextManager.executeAsTenant(otherTenant.getItemId(), () -> {
                Profile otherAliceProfile = new Profile(otherAlice);
                otherAliceProfile.setProperty("firstName", "Alice");
                otherAliceProfile.setProperty("age", 10);
                persistenceService.save(otherAliceProfile);
                Profile otherBobProfile = new Profile(otherBob);
                otherBobProfile.setProperty("firstName", "Bob");
                otherBobProfile.setProperty("age", 5);
                persistenceService.save(otherBobProfile);
            });
            refreshPersistence(Profile.class);

            Condition matchAll = matchAllCondition();
            TermsAggregate byFirstName = new TermsAggregate("properties.firstName");

            Map<String, Long> termsNoCondition = keepTrying(
                    "Terms aggregate without a condition should only count the current tenant",
                    () -> persistenceService.aggregateWithOptimizedQuery(null, byFirstName, Profile.ITEM_TYPE),
                    r -> r != null && Long.valueOf(1L).equals(r.get("Alice")) && !r.containsKey("Bob"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(Long.valueOf(1L), termsNoCondition.get("Alice"));
            Assert.assertNull("Another tenant's first names must not appear", termsNoCondition.get("Bob"));

            Map<String, Long> termsWithCondition = keepTrying(
                    "Terms aggregate with a condition should only count the current tenant",
                    () -> persistenceService.aggregateQuery(matchAll, byFirstName, Profile.ITEM_TYPE),
                    r -> r != null && Long.valueOf(1L).equals(r.get("Alice")) && !r.containsKey("Bob"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(Long.valueOf(1L), termsWithCondition.get("Alice"));
            Assert.assertNull(termsWithCondition.get("Bob"));
            long profileCount = persistenceService.getAllItemsCount(Profile.ITEM_TYPE);
            Assert.assertEquals("_all must match getAllItemsCount for the current tenant",
                    Long.valueOf(profileCount), termsWithCondition.get("_all"));
            Assert.assertEquals("With match-all, _filtered must match getAllItemsCount for the current tenant",
                    Long.valueOf(profileCount), termsWithCondition.get("_filtered"));

            Map<String, Double> metrics = keepTrying(
                    "Sum metric should only include the current tenant",
                    () -> persistenceService.getSingleValuesMetrics(matchAll, new String[]{"sum", "count"},
                            AGE_PROPERTY, Profile.ITEM_TYPE),
                    r -> r != null && r.get("_sum") != null && r.get("_sum") == 7.0,
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(7.0, metrics.get("_sum"), 0.001);
            Assert.assertEquals(1.0, metrics.get("_count"), 0.001);

            // Non-optimized path without a condition: this is where the global aggregation used to count every tenant.
            Map<String, Long> termsNonOptimizedNoCondition = keepTrying(
                    "Non-optimized terms aggregate without a condition should only count the current tenant",
                    () -> persistenceService.aggregateQuery(null, byFirstName, Profile.ITEM_TYPE),
                    r -> r != null && Long.valueOf(1L).equals(r.get("Alice")) && !r.containsKey("Bob"),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(Long.valueOf(1L), termsNonOptimizedNoCondition.get("Alice"));
            Assert.assertNull(termsNonOptimizedNoCondition.get("Bob"));
            Assert.assertEquals("_all without a condition must match getAllItemsCount for the current tenant",
                    Long.valueOf(profileCount), termsNonOptimizedNoCondition.get("_all"));
            Assert.assertNull("No condition means no _filtered", termsNonOptimizedNoCondition.get("_filtered"));

            Map<String, Double> metricsNoCondition = keepTrying(
                    "Metrics without a condition should only include the current tenant",
                    () -> persistenceService.getSingleValuesMetrics(null, new String[]{"sum", "count"},
                            AGE_PROPERTY, Profile.ITEM_TYPE),
                    r -> r != null && r.get("_sum") != null && r.get("_sum") == 7.0,
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(7.0, metricsNoCondition.get("_sum"), 0.001);
            Assert.assertEquals(1.0, metricsNoCondition.get("_count"), 0.001);

            Map<String, Long> otherTenantTerms = keepTrying(
                    "The other tenant's _all should only count its own profiles",
                    () -> executionContextManager.executeAsTenant(otherTenant.getItemId(),
                            () -> persistenceService.aggregateQuery(null, byFirstName, Profile.ITEM_TYPE)),
                    r -> r != null && Long.valueOf(2L).equals(r.get("_all")),
                    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
            Assert.assertEquals(Long.valueOf(2L), otherTenantTerms.get("_all"));
            Assert.assertEquals(Long.valueOf(1L), otherTenantTerms.get("Alice"));
            Assert.assertEquals(Long.valueOf(1L), otherTenantTerms.get("Bob"));
        } finally {
            try {
                executionContextManager.executeAsTenant(otherTenant.getItemId(), () -> {
                    persistenceService.remove(otherAlice, Profile.class);
                    persistenceService.remove(otherBob, Profile.class);
                });
            } finally {
                tenantService.deleteTenant(otherTenant.getItemId());
            }
        }
    }

    /**
     * UNOMI-1018: date histogram, numeric range and date range aggregates must return their buckets on both engines.
     */
    @Test
    public void testDateAndRangeAggregatesReturnBuckets() throws InterruptedException {
        String marker = "agg-buckets-" + UUID.randomUUID();
        saveProfileWithVisitAndAge(marker, "2020-01-01T10:00:00Z", 10);
        saveProfileWithVisitAndAge(marker, "2020-01-01T11:00:00Z", 30);
        saveProfileWithVisitAndAge(marker, "2020-01-01T12:00:00Z", 40);
        saveProfileWithVisitAndAge(marker, "2020-01-03T10:00:00Z", 70);
        refreshPersistence(Profile.class);
        Condition onlyTheseProfiles = firstNameEquals(marker);

        // "1d" is the legacy interval format: OpenSearch used to reject it
        Map<String, Long> byDay = keepTrying("Date histogram should return one bucket per day",
                () -> persistenceService.aggregateWithOptimizedQuery(onlyTheseProfiles,
                        new DateAggregate("properties.lastVisit", "1d", "yyyy-MM-dd"), Profile.ITEM_TYPE),
                r -> r != null && Long.valueOf(3L).equals(r.get("2020-01-01")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Assert.assertEquals(Long.valueOf(3L), byDay.get("2020-01-01"));
        Assert.assertEquals(Long.valueOf(0L), byDay.get("2020-01-02"));
        Assert.assertEquals(Long.valueOf(1L), byDay.get("2020-01-03"));

        Map<String, Long> byAge = keepTrying("Numeric range aggregate should return one bucket per range",
                () -> persistenceService.aggregateQuery(onlyTheseProfiles, new NumericRangeAggregate(AGE_PROPERTY,
                        Arrays.asList(numericRange("young", null, 18.0), numericRange("adult", 18.0, 65.0),
                                numericRange("senior", 65.0, null))), Profile.ITEM_TYPE),
                r -> r != null && Long.valueOf(2L).equals(r.get("adult")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Assert.assertEquals(Long.valueOf(1L), byAge.get("young"));
        Assert.assertEquals(Long.valueOf(2L), byAge.get("adult"));
        Assert.assertEquals(Long.valueOf(1L), byAge.get("senior"));
        Assert.assertEquals(Long.valueOf(4L), byAge.get("_filtered"));

        // Open-ended bounds on both sides: OpenSearch used to fail on a missing from/to
        Map<String, Long> byVisit = keepTrying("Date range aggregate should return one bucket per range",
                () -> persistenceService.aggregateWithOptimizedQuery(onlyTheseProfiles, new DateRangeAggregate(
                        "properties.lastVisit", "yyyy-MM-dd", Arrays.asList(dateRange("before", null, "2020-01-02"),
                        dateRange("after", "2020-01-02", null))), Profile.ITEM_TYPE),
                r -> r != null && Long.valueOf(3L).equals(r.get("before")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Assert.assertEquals(Long.valueOf(3L), byVisit.get("before"));
        Assert.assertEquals(Long.valueOf(1L), byVisit.get("after"));
    }

    private Condition matchAllCondition() {
        return new Condition(definitionsService.getConditionType("matchAllCondition"));
    }

    private Condition firstNameEquals(String firstName) {
        Condition condition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", "properties.firstName");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", firstName);
        return condition;
    }

    private void saveProfileWithVisitAndAge(String firstName, String lastVisit, int age) {
        String itemId = "agg-buckets-" + UUID.randomUUID();
        Profile profile = new Profile(itemId);
        profile.setProperty("firstName", firstName);
        profile.setProperty("lastVisit", Date.from(Instant.parse(lastVisit)));
        profile.setProperty("age", age);
        persistenceService.save(profile);
        profileIds.add(itemId);
    }

    private static NumericRange numericRange(String key, Double from, Double to) {
        NumericRange range = new NumericRange();
        range.setKey(key);
        range.setFrom(from);
        range.setTo(to);
        return range;
    }

    private static DateRange dateRange(String key, String from, String to) {
        DateRange range = new DateRange();
        range.setKey(key);
        range.setFrom(from);
        range.setTo(to);
        return range;
    }

    private void saveProfileWithFirstName(String itemId, String firstName) {
        Profile profile = new Profile(itemId);
        profile.setProperty("firstName", firstName);
        persistenceService.save(profile);
        profileIds.add(itemId);
    }

    private Profile saveProfile(String itemId) {
        Profile profile = new Profile(itemId);
        persistenceService.save(profile);
        profileIds.add(itemId);
        return profile;
    }

    private Event saveViewEvent(String itemId, Profile profile, String scope) {
        Event event = new Event(itemId, "view", null, profile, scope, null, null, new Date());
        persistenceService.save(event);
        eventIds.add(itemId);
        return event;
    }

    private void saveProfileWithAge(String idSuffix, int age) {
        Profile profile = new Profile();
        profile.setItemId(idSuffix + "-" + UUID.randomUUID());
        profile.setProperty("age", age);
        persistenceService.save(profile);
        profileIds.add(profile.getItemId());
    }
}
