package org.oasis_open.wemi.context.server.impl.services.cxf.cdi;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.jws.WebService;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 26.04.14.
 */
@WebService
@CXFEndPoint(url="segments")
public class SegmentServiceEndPoint implements SegmentService {

    @Inject
    @OsgiService
    SegmentService segmentService;

    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return segmentService.getMatchingIndividuals(segmentIDs);
    }

    public Boolean isUserInSegment(User user, SegmentID segmentID) {
        return segmentService.isUserInSegment(user, segmentID);
    }

    public Set<SegmentID> getSegmentsForUser(User user) {
        return segmentService.getSegmentsForUser(user);
    }

    public Set<SegmentID> getSegmentIDs() {
        return segmentService.getSegmentIDs();
    }

    public Set<SegmentDefinition> getSegmentDefinition(SegmentID segmentID) {
        return segmentService.getSegmentDefinition(segmentID);
    }

    public Set<ConditionTag> getConditionTags() {
        return segmentService.getConditionTags();
    }

    public Set<Condition> getConditions(ConditionTag conditionTag) {
        return segmentService.getConditions(conditionTag);
    }

    public Set<ConditionParameter> getConditionParameters(Condition condition) {
        return segmentService.getConditionParameters(condition);
    }
}
