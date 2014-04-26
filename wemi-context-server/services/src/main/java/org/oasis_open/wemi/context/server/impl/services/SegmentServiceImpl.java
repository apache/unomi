package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 26.04.14.
 */
@ApplicationScoped
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService {
    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return null;
    }

    public Boolean isUserInSegment(User user, SegmentID segmentID) {
        return null;
    }

    public Set<SegmentID> getSegmentsForUser(User user) {
        return null;
    }

    public Set<SegmentID> getSegmentIDs() {
        return null;
    }

    public Set<SegmentDefinition> getSegmentDefinition(SegmentID segmentID) {
        return null;
    }

    public Set<ConditionTag> getConditionTags() {
        return null;
    }

    public Set<Condition> getConditions(ConditionTag conditionTag) {
        return null;
    }

    public Set<ConditionParameter> getConditionParameters(Condition condition) {
        return null;
    }
}
