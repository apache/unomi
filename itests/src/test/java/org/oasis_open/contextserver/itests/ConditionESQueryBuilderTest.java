package org.oasis_open.contextserver.itests;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 * Integration tests for various condition query builder types (elasticsearch).
 * 
 * @author Sergiy Shyrkov
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ConditionESQueryBuilderTest extends ConditionEvaluatorTest {

    private Segment segment;

    private String segmentId;

    @Inject
    private SegmentService segmentService;

    @Inject
    private DefinitionsService definitionsService;

    @Override
    protected boolean eval(Condition c) {
        updateSegmentCondition(c);

        return segmentService.isProfileInSegment(profile, segment.getMetadata().getScope(), segmentId);
    }

    @Before
    public void setUp() {
        super.setUp();

        segmentId = "ConditionESQueryBuilderTest";
        segment = segmentService.getSegmentDefinition(Metadata.SYSTEM_SCOPE, segmentId);
        if (segment != null) {
            segmentService.removeSegmentDefinition(Metadata.SYSTEM_SCOPE, segmentId);
        }

        Metadata metadata = new Metadata(Metadata.SYSTEM_SCOPE, segmentId, segmentId + " Segment", "");
        Segment recreateSegment = new Segment(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        rootCondition.getParameterValues().put("operator", "and");
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        recreateSegment.setCondition(rootCondition);
        segmentService.setSegmentDefinition(recreateSegment);

        segment = segmentService.getSegmentDefinition(Metadata.SYSTEM_SCOPE, segmentId);
        assertNotNull("Segment has not been created", segment);
    }

    @After
    public void tearDown() {
        if (segment != null) {
            segmentService.removeSegmentDefinition(Metadata.SYSTEM_SCOPE, segmentId);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateSegmentCondition(Condition c) {
        List<Condition> subConditions = (List<Condition>) segment.getCondition().getParameterValues()
                .get("subConditions");
        subConditions.clear();
        subConditions.add(c);
        segmentService.setSegmentDefinition(segment);
    }
}
