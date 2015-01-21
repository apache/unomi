package org.oasis_open.contextserver.itests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for various condition types.
 * 
 * @author Sergiy Shyrkov
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ConditionTest extends BaseTest {

    private final static Logger logger = LoggerFactory.getLogger(ConditionTest.class);

    @Inject
    private DefinitionsService definitionsService;

    @Inject
    private PersistenceService persistenceService;

    private String profileId1;

    private String profileId2;

    @Inject
    private ProfileService profileService;

    @Inject
    private SegmentService segmentService;

    private void createProfiles() {
        profileId1 = "p1-" + UUID.randomUUID().toString();
        Profile profile1 = new Profile(profileId1);
        profile1.setProperty("firstVisit", new Date());
        profile1.setProperty("age", Integer.valueOf(30));
        profile1.setProperty("gender", "female");
        profileService.save(profile1);

        logger.info("Created profile: {}", profile1);

        profileId2 = "p2-" + UUID.randomUUID().toString();
        Profile profile2 = new Profile(profileId2);
        profile2.setProperty("firstVisit", new Date());
        profile2.setProperty("age", Integer.valueOf(35));
        profile2.setProperty("gender", "male");
        profileService.save(profile2);

        logger.info("Created profile: {}", profile2);
    }

    @Before
    public void setUp() {
        assertNotNull("Definition service should be available", definitionsService);
        assertNotNull("Persistence service should be available", persistenceService);
        assertNotNull("Profile service should be available", profileService);
        assertNotNull("Segment service should be available", segmentService);
    }

    // @Test
    public void testCreateProfiles() {
        createProfiles();
    }

    // @Test
    @SuppressWarnings("unchecked")
    public void testCreateSegments() {
        createProfiles();

        segmentService.createSegmentDefinition(Metadata.SYSTEM_SCOPE, "segment-1-females", "Segment 1 - Females", "");

        Segment segment1 = segmentService.getSegmentDefinition(Metadata.SYSTEM_SCOPE, "segment-1-females");

        assertNotNull("Segment has not been created", segment1);
        logger.info("Created segment: {}", segment1);

        Condition cond = new Condition();
        cond.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        cond.getParameterValues().put("propertyName", "properties.gender");
        cond.getParameterValues().put("comparisonOperator", "equals");
        cond.getParameterValues().put("propertyValue", "female");
        ((List<Condition>) segment1.getCondition().getParameterValues().get("subConditions")).add(cond);

        // save segment
        segmentService.setSegmentDefinition(segment1);

        // reload segment
        segment1 = segmentService.getSegmentDefinition(Metadata.SYSTEM_SCOPE, "segment-1-females");

        logger.info("Sub-conditions: {}", segment1.getCondition().getParameterValues().get("subConditions"));

        Profile p = profileService.load(profileId1);

        // assertTrue("Profile 1 should match the segment 1",
        // segmentService.isProfileInSegment(p, segment1.getMetadata().getScope(), segment1.getItemId()));

        logger.info("Profile 1 matches condition", persistenceService.testMatch(cond, p));
    }

    @Test
    public void testPropertyEvaluatorCompound() {
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
    public void testPropertyEvaluatorDate() {
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
    public void testPropertyEvaluatorExistence() {
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
    public void testPropertyEvaluatorInteger() {
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
    public void testPropertyEvaluatorMultiValue() {
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
    public void testPropertyEvaluatorString() {
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
