package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
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

    @WebMethod(exclude=true)
    public Set<User> getMatchingIndividuals(String segmentId) {
        return segmentService.getMatchingIndividuals(segmentId);
    }

    @WebMethod(exclude=true)
    public Boolean isUserInSegment(User user, String segmentId) {
        return segmentService.isUserInSegment(user, segmentId);
    }

    @WebMethod(exclude=true)
    public Set<String> getSegmentsForUser( User user) {
        return segmentService.getSegmentsForUser(user);
    }

    @GET
    @Path("/")
    public Set<Metadata> getSegmentMetadatas() {
        return segmentService.getSegmentMetadatas();
    }

    @GET
    @Path("/{segmentID}")
    public SegmentDefinition getSegmentDefinition(@PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDefinition(segmentId);
    }

    @POST
    @Path("/{segmentID}")
    public void setSegmentDefinition(@PathParam("segmentID") String segmentId, SegmentDefinition segmentDefinition) {
        segmentService.setSegmentDefinition(segmentId, segmentDefinition);
    }

    @PUT
    @Path("/{segmentID}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createSegmentDefinition(@PathParam("segmentID") String segmentId, @FormParam("segmentName") String segmentName, @FormParam("segmentDescription") String segmentDescription) {
        segmentService.createSegmentDefinition(segmentId, segmentName, segmentDescription);
    }

    @DELETE
    @Path("/{segmentID}")
    public void removeSegmentDefinition(@PathParam("segmentID") String segmentId) {
        segmentService.removeSegmentDefinition(segmentId);
    }

}
