package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.services.SegmentService;

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
public class SegmentServiceEndPoint {

    SegmentService segmentService;

    public SegmentServiceEndPoint() {
        System.out.println("Initializing segment service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GET
    @Path("/{scope}/{segmentID}/match")
    public PartialList<Profile> getMatchingIndividuals(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getMatchingIndividuals(scope, segmentId, offset, size, sortBy);
    }

    @GET
    @Path("/{scope}/{segmentID}/count")
    public long getMatchingIndividualsCount(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId) {
        return segmentService.getMatchingIndividualsCount(scope, segmentId);
    }

    @GET
    @Path("/{scope}/{segmentID}/match/{profile}")
    public Boolean isProfileInSegment(@PathParam("profile") Profile profile, @PathParam("scope") String scope, @PathParam("segmentID") String segmentId) {
        return segmentService.isProfileInSegment(profile, scope, segmentId);
    }

    @GET
    @Path("/")
    public Set<Metadata> getSegmentMetadatas() {
        return segmentService.getSegmentMetadatas();
    }

    @GET
    @Path("/{scope}")
    public Set<Metadata> getSegmentMetadatas(@PathParam("scope") String scope) {
        return segmentService.getSegmentMetadatas(scope);
    }

    @GET
    @Path("/{scope}/{segmentID}")
    public Segment getSegmentDefinition(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDefinition(scope, segmentId);
    }

    @POST
    @Path("/{scope}/{segmentID}")
    public void setSegmentDefinition(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId, Segment segment) {
        segmentService.setSegmentDefinition(segment);
    }

    @PUT
    @Path("/{scope}/{segmentID}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createSegmentDefinition(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId, @FormParam("segmentName") String segmentName, @FormParam("segmentDescription") String segmentDescription) {
        segmentService.createSegmentDefinition(scope, segmentId, segmentName, segmentDescription);
    }

    @DELETE
    @Path("/{scope}/{segmentID}")
    public void removeSegmentDefinition(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId) {
        segmentService.removeSegmentDefinition(scope, segmentId);
    }

    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : segmentService.getSegmentMetadatas()) {
            Segment s = segmentService.getSegmentDefinition(metadata.getScope(), metadata.getId());
            segmentService.setSegmentDefinition(s);
        }
    }

}
