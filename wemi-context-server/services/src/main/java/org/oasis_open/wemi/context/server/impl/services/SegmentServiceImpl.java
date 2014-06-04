package org.oasis_open.wemi.context.server.impl.services;

import org.mvel2.MVEL;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import java.io.Serializable;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
@ApplicationScoped
@Default
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService {

    Map<SegmentID, Serializable> segmentExpressions = new LinkedHashMap<SegmentID, Serializable>();

    public SegmentServiceImpl() {
        System.out.println("Initializing segment service...");

        // @Todo remove hardcoded segments, make them configurable.
        segmentExpressions.put(new SegmentID("alwaysTrue", "All users", "This segment includes all users"), MVEL.compileExpression("true"));
        segmentExpressions.put(new SegmentID("maleGender", "Men", "This segment includes all men"), MVEL.compileExpression("user.properties.?gender == 'male'"));

    }

    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return null;
    }

    public Boolean isUserInSegment(User user, SegmentID segmentID) {

        Set<SegmentID> matchingSegments = getSegmentsForUser(user);

        return matchingSegments.contains(segmentID);
    }

    public Set<SegmentID> getSegmentsForUser(User user) {

        Set<SegmentID> matchedSegments = new LinkedHashSet<SegmentID>();

        Map vars = new HashMap();
        vars.put("user", user);

        for (Map.Entry<SegmentID, Serializable> segmentExpressionEntry : segmentExpressions.entrySet()) {

            // Now we execute it.
            Boolean result = (Boolean) MVEL.executeExpression(segmentExpressionEntry.getValue(), vars);

            if (result.booleanValue()) {
                matchedSegments.add(segmentExpressionEntry.getKey());
            }

        }

        return matchedSegments;
    }

    public Set<SegmentID> getSegmentIDs() {
        return segmentExpressions.keySet();
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
