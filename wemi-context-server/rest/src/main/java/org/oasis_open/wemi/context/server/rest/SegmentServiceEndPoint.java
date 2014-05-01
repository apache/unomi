package org.oasis_open.wemi.context.server.rest;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 26.04.14.
 */
@WebService
public class SegmentServiceEndPoint implements SegmentService {

    SegmentService segmentService;

    public SegmentServiceEndPoint() {
        System.out.println("Initializing segment service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Path("{segmentIDs}")
    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return segmentService.getMatchingIndividuals(segmentIDs);
    }

    @Path("{segmentID}/{user}")
    public Boolean isUserInSegment(@PathParam("user") User user, @PathParam("segmentID") SegmentID segmentID) {
        return segmentService.isUserInSegment(user, segmentID);
    }

    @Path("/users/{user}")
    public Set<SegmentID> getSegmentsForUser(@PathParam("user") User user) {
        return segmentService.getSegmentsForUser(user);
    }

    @Path("/")
    public Set<SegmentID> getSegmentIDs() {
        return segmentService.getSegmentIDs();
    }

    @Path("/definitions/{segmentID}")
    public Set<SegmentDefinition> getSegmentDefinition(@PathParam("segmentID") SegmentID segmentID) {
        return segmentService.getSegmentDefinition(segmentID);
    }

    @Path("/definitions/conditions/tags")
    public Set<ConditionTag> getConditionTags() {
        return segmentService.getConditionTags();
    }

    @Path("/definitions/conditions/tags/{tagId}")
    public Set<Condition> getConditions(@PathParam("tagId") ConditionTag conditionTag) {
        return segmentService.getConditions(conditionTag);
    }

    @Path("/definitions/conditions/{conditionId}")
    public Set<ConditionParameter> getConditionParameters(@PathParam("conditionId") Condition condition) {
        return segmentService.getConditionParameters(condition);
    }
}
