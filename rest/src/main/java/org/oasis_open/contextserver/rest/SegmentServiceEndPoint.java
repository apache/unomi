package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class SegmentServiceEndPoint {

    private SegmentService segmentService;

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

    @POST
    @Path("/")
    public void setSegmentDefinition(Segment segment) {
        segmentService.setSegmentDefinition(segment);
    }

    @GET
    @Path("/{scope}")
    public Set<Metadata> getSegmentMetadatas(@PathParam("scope") String scope) {
        return segmentService.getSegmentMetadatas(scope);
    }

    @GET
    @Path("/scoped")
    public Map<String, Set<Metadata>> getScopedSegmentMetadata() {
        return segmentService.getScopedSegmentMetadata(null, true);
    }

    @GET
    @Path("/scoped/{scope}")
    public Map<String, Set<Metadata>> getScopedSegmentMetadata(@PathParam("scope") String scope, @QueryParam("includeShared") boolean includeShared) {
        return segmentService.getScopedSegmentMetadata(scope, includeShared);
    }

    @GET
    @Path("/{scope}/{segmentID}")
    public Segment getSegmentDefinition(@PathParam("scope") String scope, @PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDefinition(scope, segmentId);
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
