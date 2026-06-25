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
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Integration tests for {@link org.apache.unomi.persistence.spi.PersistenceService} query APIs against the live
 * search backend (Elasticsearch or OpenSearch). Initial coverage focuses on {@code rangeQuery}; additional methods
 * should be covered in follow-up work (UNOMI-956).
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PersistenceServiceIT extends BaseIT {

    private static final String AGE_PROPERTY = "properties.age";

    private final List<String> profileIds = new ArrayList<>();

    @After
    public void tearDown() throws InterruptedException {
        for (String profileId : profileIds) {
            persistenceService.remove(profileId, Profile.class);
        }
        profileIds.clear();
        refreshPersistence(Profile.class);
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

    private void saveProfileWithAge(String idSuffix, int age) {
        Profile profile = new Profile();
        profile.setItemId(idSuffix + "-" + UUID.randomUUID());
        profile.setProperty("age", age);
        persistenceService.save(profile);
        profileIds.add(profile.getItemId());
    }
}
