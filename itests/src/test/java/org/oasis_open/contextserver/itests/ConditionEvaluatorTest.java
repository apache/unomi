package org.oasis_open.contextserver.itests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 * Integration tests for various condition types.
 * 
 * @author Sergiy Shyrkov
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ConditionEvaluatorTest extends BaseTest {

    @Inject
    private DefinitionsService definitionsService;

    @Inject
    private PersistenceService persistenceService;

    @Before
    public void setUp() {
        assertNotNull("Definition service should be available", definitionsService);
        assertNotNull("Persistence service should be available", persistenceService);
    }

    @Test
    public void testCompound() {
        Date lastVisit = new Date();
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("lastVisit", lastVisit);
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        // test AND
        assertTrue(persistenceService.testMatch(
                builder.and(builder.profileProperty("properties.gender").equalTo("female"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build(), female));
        assertFalse(persistenceService.testMatch(
                builder.and(builder.profileProperty("properties.gender").equalTo("male"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build(), female));
        assertFalse(persistenceService.testMatch(
                builder.and(builder.profileProperty("properties.gender").equalTo("female"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build(), female));

        // test OR
        assertTrue(persistenceService.testMatch(
                builder.or(builder.profileProperty("properties.gender").equalTo("female"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build(), female));
        assertTrue(persistenceService.testMatch(
                builder.or(builder.profileProperty("properties.gender").equalTo("male"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build(), female));
        assertFalse(persistenceService.testMatch(
                builder.or(builder.profileProperty("properties.gender").equalTo("male"),
                        builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build(), female));

        // test NOT
        assertTrue(persistenceService.testMatch(
                builder.not(builder.profileProperty("properties.gender").equalTo("male")).build(), female));
        assertFalse(persistenceService.testMatch(
                builder.not(builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build(), female));

    }

    @Test
    public void testDate() {
        Date lastVisit = new Date();
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("lastVisit", lastVisit);
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        // test date property
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.lastVisit").equalTo(lastVisit)
                .build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.lastVisit").greaterThan(new Date(lastVisit.getTime() - 10000))
                        .build(), female));
        assertTrue(persistenceService
                .testMatch(
                        builder.profileProperty("properties.lastVisit").lessThan(new Date(lastVisit.getTime() + 10000))
                                .build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.lastVisit")
                        .in(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000), lastVisit)
                        .build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.lastVisit")
                        .notIn(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000)).build(),
                female));
        assertFalse(persistenceService.testMatch(
                builder.profileProperty("properties.lastVisit")
                        .notIn(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000), lastVisit)
                        .build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.lastVisit").all(new Date(lastVisit.getTime() + 10000), lastVisit)
                        .build(), female));
    }

    @Test
    public void testExistence() {
        Date lastVisit = new Date();
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("lastVisit", lastVisit);
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        // test existence
        assertTrue("Gender property does not exist",
                persistenceService.testMatch(builder.profileProperty("properties.gender").exists().build(), female));
        assertFalse("Gender property missing",
                persistenceService.testMatch(builder.profileProperty("properties.gender").missing().build(), female));
        assertTrue("Strange property exists",
                persistenceService.testMatch(builder.profileProperty("properties.unknown").missing().build(), female));
        assertFalse("Strange property exists",
                persistenceService.testMatch(builder.profileProperty("properties.unknown").exists().build(), female));
    }

    @Test
    public void testInteger() {
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        // test integer property
        assertTrue(

        persistenceService.testMatch(builder.profileProperty("properties.age").equalTo(Integer.valueOf(30)).build(),
                female));
        assertTrue(persistenceService.testMatch(
                builder.not(builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build(), female));
        assertTrue(

        persistenceService.testMatch(builder.profileProperty("properties.age").notEqualTo(Integer.valueOf(40)).build(),
                female));
        assertTrue(

        persistenceService.testMatch(builder.profileProperty("properties.age").lessThan(Integer.valueOf(40)).build(),
                female));
        assertTrue(

        persistenceService.testMatch(
                builder.profileProperty("properties.age").greaterThan(Integer.valueOf(20)).build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.age").greaterThanOrEqualTo(Integer.valueOf(30)).build(), female));
        assertFalse(persistenceService.testMatch(
                builder.profileProperty("properties.age").greaterThanOrEqualTo(Integer.valueOf(31)).build(), female));

        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.age").in(Integer.valueOf(31), Integer.valueOf(30)).build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.age").notIn(Integer.valueOf(25), Integer.valueOf(26)).build(),
                female));
        assertFalse(persistenceService.testMatch(
                builder.profileProperty("properties.age").notIn(Integer.valueOf(25), Integer.valueOf(30)).build(),
                female));
    }

    @Test
    public void testMultiValue() {
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");
        female.setSegments(new HashSet<String>(Arrays.asList("s1", "s2")));

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        assertTrue(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "in")
                        .parameter("segments", "s10", "s20", "s2").build(), female));
        assertFalse(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "in")
                        .parameter("segments", "s10", "s20", "s30").build(), female));
        assertTrue(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "notIn")
                        .parameter("segments", "s10", "s20", "s30").build(), female));
        assertFalse(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "notIn")
                        .parameter("segments", "s10", "s20", "s2").build(), female));
        assertTrue(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "all")
                        .parameter("segments", "s1", "s2", "s3", "s4").build(), female));
        assertFalse(persistenceService.testMatch(
                builder.property("profileSegmentCondition", "segments").parameter("matchType", "all")
                        .parameter("segments", "s1").build(), female));
    }

    @Test
    public void testString() {
        Date lastVisit = new Date();
        Profile female = new Profile("profile-" + UUID.randomUUID().toString());
        female.setProperty("lastVisit", lastVisit);
        female.setProperty("age", Integer.valueOf(30));
        female.setProperty("gender", "female");

        ConditionBuilder builder = new ConditionBuilder(definitionsService);

        // test string property
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").equalTo("female").build(),
                female));
        assertFalse(persistenceService.testMatch(
                builder.not(builder.profileProperty("properties.gender").equalTo("female")).build(), female));
        assertTrue(persistenceService.testMatch(
                builder.profileProperty("properties.gender").notEqualTo("male").build(), female));
        assertFalse(

        persistenceService.testMatch(builder.not(builder.profileProperty("properties.gender").notEqualTo("male"))
                .build(), female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").startsWith("fe").build(),
                female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").endsWith("le").build(),
                female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").contains("fem").build(),
                female));
        assertFalse(persistenceService.testMatch(builder.profileProperty("properties.gender").contains("mu").build(),
                female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").matchesRegex(".*ale")
                .build(), female));

        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").in("male", "female")
                .build(), female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").notIn("one", "two")
                .build(), female));
        assertFalse(persistenceService.testMatch(
                builder.profileProperty("properties.gender").notIn("one", "two", "female").build(), female));
        assertTrue(persistenceService.testMatch(builder.profileProperty("properties.gender").all("male", "female")
                .build(), female));
    }
}
