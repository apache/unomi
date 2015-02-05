package org.oasis_open.contextserver.itests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.*;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
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

    protected ConditionBuilder builder;

    @Inject
    private DefinitionsService definitionsService;

    @Inject
    protected PersistenceService persistenceService;

    protected Item item;
    protected Date lastVisit;

    protected boolean eval(Condition c) {
        return persistenceService.testMatch(c, item);
    }

    @Before
    public void setUp() {
        assertNotNull("Definition service should be available", definitionsService);
        assertNotNull("Persistence service should be available", persistenceService);

        lastVisit = new GregorianCalendar(2015,1,1,20,30,0).getTime();

        Profile profile = new Profile("profile-" + UUID.randomUUID().toString());
        profile.setProperty("age", Integer.valueOf(30));
        profile.setProperty("gender", "female");
        profile.setProperty("lastVisit", lastVisit);
        profile.setSegments(new HashSet<String>(Arrays.asList("s1", "s2", "s3")));
        this.item = profile;
        builder = new ConditionBuilder(definitionsService);
    }

    @Test
    public void testCompound() {
        // test AND
        assertTrue(eval(builder.and(builder.profileProperty("properties.gender").equalTo("female"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build()));
        assertFalse(eval(builder.and(builder.profileProperty("properties.gender").equalTo("male"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build()));
        assertFalse(eval(builder.and(builder.profileProperty("properties.gender").equalTo("female"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build()));

        // test OR
        assertTrue(eval(builder.or(builder.profileProperty("properties.gender").equalTo("female"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build()));
        assertTrue(eval(builder.or(builder.profileProperty("properties.gender").equalTo("male"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build()));
        assertFalse(eval(builder.or(builder.profileProperty("properties.gender").equalTo("male"),
                builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build()));

        // test NOT
        assertTrue(eval(builder.not(builder.profileProperty("properties.gender").equalTo("male")).build()));
        assertFalse(eval(builder.not(builder.profileProperty("properties.age").equalTo(Integer.valueOf(30))).build()));

    }

    @Test
    public void testDate() {
        assertTrue(eval(builder.profileProperty("properties.lastVisit").equalTo(lastVisit).build()));
        assertTrue(eval(builder.profileProperty("properties.lastVisit")
                .greaterThan(new Date(lastVisit.getTime() - 10000)).build()));
        assertTrue(eval(builder.profileProperty("properties.lastVisit").lessThan(new Date(lastVisit.getTime() + 10000))
                .build()));
        assertTrue(eval(builder.profileProperty("properties.lastVisit")
                .in(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000), lastVisit).build()));
        assertTrue(eval(builder.profileProperty("properties.lastVisit")
                .notIn(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000)).build()));
        assertFalse(eval(builder.profileProperty("properties.lastVisit")
                .notIn(new Date(lastVisit.getTime() + 10000), new Date(lastVisit.getTime() - 10000), lastVisit).build()));
        assertTrue(eval(builder.profileProperty("properties.lastVisit").all(lastVisit).build()));
        assertFalse(eval(builder.profileProperty("properties.lastVisit")
                .all(new Date(lastVisit.getTime() + 10000), lastVisit).build()));
    }

    @Test
    public void testExistence() {
        assertTrue("Gender property does not exist",
                eval(builder.profileProperty("properties.gender").exists().build()));
        assertFalse("Gender property missing", eval(builder.profileProperty("properties.gender").missing().build()));
        assertTrue("Strange property exists", eval(builder.profileProperty("properties.unknown").missing().build()));
        assertFalse("Strange property exists", eval(builder.profileProperty("properties.unknown").exists().build()));
    }

    @Test
    public void testInteger() {
        assertTrue(eval(builder.profileProperty("properties.age").equalTo(Integer.valueOf(30)).build()));
        assertTrue(eval(builder.not(builder.profileProperty("properties.age").equalTo(Integer.valueOf(40))).build()));
        assertTrue(eval(builder.profileProperty("properties.age").notEqualTo(Integer.valueOf(40)).build()));
        assertTrue(eval(builder.profileProperty("properties.age").lessThan(Integer.valueOf(40)).build()));
        assertTrue(eval(builder.profileProperty("properties.age").greaterThan(Integer.valueOf(20)).build()));
        assertTrue(eval(builder.profileProperty("properties.age").greaterThanOrEqualTo(Integer.valueOf(30)).build()));
        assertFalse(eval(builder.profileProperty("properties.age").greaterThanOrEqualTo(Integer.valueOf(31)).build()));

        assertTrue(eval(builder.profileProperty("properties.age").in(Integer.valueOf(30)).build()));
        assertTrue(eval(builder.profileProperty("properties.age").in(Integer.valueOf(31), Integer.valueOf(30)).build()));
        assertTrue(eval(builder.profileProperty("properties.age").notIn(Integer.valueOf(25), Integer.valueOf(26))
                .build()));
        assertFalse(eval(builder.profileProperty("properties.age").notIn(Integer.valueOf(25), Integer.valueOf(30))
                .build()));
    }

    @Test
    public void testMultiValue() {
        assertTrue(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "in")
                .parameter("segments", "s10", "s20", "s2").build()));
        assertFalse(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "in")
                .parameter("segments", "s10", "s20", "s30").build()));
        assertTrue(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "notIn")
                .parameter("segments", "s10", "s20", "s30").build()));
        assertFalse(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "notIn")
                .parameter("segments", "s10", "s20", "s2").build()));
        assertTrue(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "all")
                .parameter("segments", "s1", "s2").build()));
        assertFalse(eval(builder.property("profileSegmentCondition", "segments").parameter("matchType", "all")
                .parameter("segments", "s1", "s5").build()));
    }

    @Test
    public void testString() {
        assertTrue(eval(builder.profileProperty("properties.gender").equalTo("female").build()));
        assertFalse(eval(builder.not(builder.profileProperty("properties.gender").equalTo("female")).build()));
        assertTrue(eval(builder.profileProperty("properties.gender").notEqualTo("male").build()));
//        assertFalse(eval(builder.not(builder.profileProperty("properties.gender").notEqualTo("male")).build()));
        assertTrue(eval(builder.profileProperty("properties.gender").startsWith("fe").build()));
        assertTrue(eval(builder.profileProperty("properties.gender").endsWith("le").build()));
        assertTrue(eval(builder.profileProperty("properties.gender").contains("fem").build()));
        assertFalse(eval(builder.profileProperty("properties.gender").contains("mu").build()));
        assertTrue(eval(builder.profileProperty("properties.gender").matchesRegex(".*ale").build()));

        assertTrue(eval(builder.profileProperty("properties.gender").in("male", "female").build()));
        assertTrue(eval(builder.profileProperty("properties.gender").notIn("one", "two").build()));
        assertFalse(eval(builder.profileProperty("properties.gender").notIn("one", "two", "female").build()));
        assertTrue(eval(builder.profileProperty("properties.gender").all("female").build()));
        assertFalse(eval(builder.profileProperty("properties.gender").all("male", "female").build()));
    }
}
