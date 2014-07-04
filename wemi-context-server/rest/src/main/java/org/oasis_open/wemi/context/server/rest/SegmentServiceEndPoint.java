package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 26.04.14.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
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
    @Path("/{segmentID}")
    public Set<User> getMatchingIndividuals(@PathParam("segmentID") SegmentID segmentID) {
        return segmentService.getMatchingIndividuals(segmentID);
    }

    @GET
    @Path("/{segmentID}/{user}")
    public Boolean isUserInSegment(@PathParam("user") User user, @PathParam("segmentID") SegmentID segmentID) {
        return segmentService.isUserInSegment(user, segmentID);
    }

    @GET
    @Path("/users/{user}")
    public Set<SegmentID> getSegmentsForUser(@PathParam("user") User user) {
        return segmentService.getSegmentsForUser(user);
    }

    @GET
    @Path("/")
    public Set<SegmentID> getSegmentIDs() {
        return segmentService.getSegmentIDs();
    }

    @GET
    @Path("/definitions/{segmentID}")
    public SegmentDefinition getSegmentDefinition(@PathParam("segmentID") SegmentID segmentID) {
        return segmentService.getSegmentDefinition(segmentID);
    }

    @POST
    @Path("/definitions/{segmentID}")
    public void setSegmentDefinition(@PathParam("segmentID") SegmentID segmentID, @FormParam("segmentDefinition") SegmentDefinition segmentDefinition) {
        segmentService.setSegmentDefinition(segmentID, segmentDefinition);
    }

}
