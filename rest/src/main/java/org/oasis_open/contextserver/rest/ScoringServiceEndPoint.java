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
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.segments.Scoring;
import org.oasis_open.contextserver.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ScoringServiceEndPoint {

    private SegmentService segmentService;

    public ScoringServiceEndPoint() {
        System.out.println("Initializing scoring service endpoint...");
    }

    @WebMethod(exclude=true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GET
    @Path("/")
    public Set<Metadata> getScoringMetadatas() {
        return segmentService.getScoringMetadatas();
    }

    @POST
    @Path("/query")
    public Set<Metadata> getScoringMetadatas(Query query) {
        return segmentService.getScoringMetadatas(query);
    }

    @GET
    @Path("/{scoringID}")
    public Scoring getScoringDefinition(@PathParam("scoringID") String scoringId) {
        return segmentService.getScoringDefinition(scoringId);
    }

    @POST
    @Path("/")
    public void setScoringDefinition(Scoring scoring) {
        segmentService.setScoringDefinition(scoring);
    }

    @PUT
    @Path("/{scope}/{scoringID}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createScoringDefinition(@PathParam("scope") String scope, @PathParam("scoringID") String scoringId, @FormParam("scoringName") String scoringName, @FormParam("scoringDescription") String scoringDescription) {
        segmentService.createScoringDefinition(scope, scoringId, scoringName, scoringDescription);
    }

    @DELETE
    @Path("/{scoringID}")
    public void removeScoringDefinition(@PathParam("scoringID") String scoringId) {
        segmentService.removeScoringDefinition(scoringId);
    }

    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : segmentService.getScoringMetadatas()) {
            Scoring s = segmentService.getScoringDefinition(metadata.getId());
            segmentService.setScoringDefinition(s);
        }
    }

}
