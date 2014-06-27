package org.oasis_open.wemi.context.server.rest;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 26.04.14.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
public class SegmentServiceEndPoint implements SegmentService {

    SegmentService segmentService;

    public SegmentServiceEndPoint() {
        System.out.println("Initializing segment service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GET
    @Path("/segments/{segmentIDs}")
    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return segmentService.getMatchingIndividuals(segmentIDs);
    }

    @GET
    @Path("/segments/{segmentID}/{user}")
    public Boolean isUserInSegment(@PathParam("user") User user, @PathParam("segmentID") SegmentID segmentID) {
        return segmentService.isUserInSegment(user, segmentID);
    }

    @GET
    @Path("/users/{user}")
    public Set<SegmentID> getSegmentsForUser(@PathParam("user") User user) {
        return segmentService.getSegmentsForUser(user);
    }

    @GET
    @Path("/segments")
    public Set<SegmentID> getSegmentIDs() {
        return segmentService.getSegmentIDs();
    }

    @GET
    @Path("/definitions/{segmentID}")
    public SegmentDefinition getSegmentDefinition(@PathParam("segmentID") SegmentID segmentID) {
        return segmentService.getSegmentDefinition(segmentID);
    }

    @GET
    @Path("/definitions/conditions/tags")
    public Set<ConditionTag> getConditionTags() {
        return segmentService.getConditionTags();
    }

    @GET
    @Path("/definitions/conditions/tags/{tagId}")
    public Set<ConditionType> getConditions(@PathParam("tagId") ConditionTag conditionTag) {
        return segmentService.getConditions(conditionTag);
    }

    @GET
    @Path("/definitions/conditions/{conditionId}")
    public List<ConditionParameter> getConditionParameters(@PathParam("conditionId") ConditionType condition) {
        return segmentService.getConditionParameters(condition);
    }
}
