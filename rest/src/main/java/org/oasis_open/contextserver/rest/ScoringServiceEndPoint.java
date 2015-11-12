/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.segments.Scoring;
import org.oasis_open.contextserver.api.services.SegmentService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link Scoring}s
 */
@Path("/scoring")
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

    @WebMethod(exclude = true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    /**
     * Retrieves the set of all scoring metadata.
     *
     * @return the set of all scoring metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getScoringMetadatas() {
        return segmentService.getScoringMetadatas(0, 50, null).getList();
    }

    /**
     * Retrieves the set of scoring metadata for scorings matching the specified query.
     *
     * @param query the query the scorings must match for their metadata to be retrieved
     * @return the set of scoring metadata for scorings matching the specified query
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getScoringMetadatas(Query query) {
        return segmentService.getScoringMetadatas(query);
    }

    /**
     * Retrieves the scoring identified by the specified identifier.
     *
     * @param scoringId the identifier of the scoring to be retrieved
     * @return the scoring identified by the specified identifier or {@code null} if no such scoring exists
     */
    @GET
    @Path("/{scoringID}")
    public Scoring getScoringDefinition(@PathParam("scoringID") String scoringId) {
        return segmentService.getScoringDefinition(scoringId);
    }

    /**
     * Persists the specified scoring in the context server.
     *
     * @param scoring the scoring to be persisted
     */
    @POST
    @Path("/")
    public void setScoringDefinition(Scoring scoring) {
        segmentService.setScoringDefinition(scoring);
    }

    /**
     * Creates a scoring with the specified scope, identifier, name and description from form-encoded data.
     *
     * @param scope              the scope for the new scoring
     * @param scoringId          the identifier for the new scoring
     * @param scoringName        the name of the new scoring
     * @param scoringDescription the description of the new scoring
     * @see Item Item's description for a discussion of scope
     */
    @PUT
    @Path("/{scope}/{scoringID}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createScoringDefinition(@PathParam("scope") String scope, @PathParam("scoringID") String scoringId, @FormParam("scoringName") String scoringName, @FormParam("scoringDescription") String scoringDescription) {
        segmentService.createScoringDefinition(scope, scoringId, scoringName, scoringDescription);
    }

    /**
     * Deletes the scoring identified by the specified identifier from the context server.
     *
     * @param scoringId the identifier of the scoring to be deleted
     */
    @DELETE
    @Path("/{scoringID}")
    public void removeScoringDefinition(@PathParam("scoringID") String scoringId) {
        segmentService.removeScoringDefinition(scoringId);
    }

    /**
     * TODO: remove
     *
     * @deprecated not needed anymore
     */
    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : segmentService.getScoringMetadatas(0, 50, null).getList()) {
            Scoring s = segmentService.getScoringDefinition(metadata.getId());
            segmentService.setScoringDefinition(s);
        }
    }

}
