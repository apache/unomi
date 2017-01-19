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

package org.apache.unomi.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.services.SegmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link Scoring}s
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ScoringServiceEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(ScoringServiceEndPoint.class.getName());

    private SegmentService segmentService;

    public ScoringServiceEndPoint() {
        logger.info("Initializing scoring service endpoint...");
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
     * Removes the scoring definition identified by the specified identifier. We can specify that we want the operation to be validated beforehand so that we can
     * know if any other segment that might use the segment we're trying to delete as a condition might be impacted. If {@code validate} is set to {@code false}, no
     * validation is performed. If set to {@code true}, we will first check if any segment or scoring depends on the scoring we're trying to delete and if so we will not delete the
     * scoring but rather return the list of the metadata of the impacted items. If no dependents are found, then we properly delete the scoring.
     *
     * @param scoringId the identifier of the scoring we want to delete
     * @param validate  whether or not to perform validation
     * @return a list of impacted items metadata if any or an empty list if none were found or validation was skipped
     */
   @DELETE
    @Path("/{scoringID}")
    public DependentMetadata removeScoringDefinition(@PathParam("scoringID") String scoringId, @QueryParam("validate") boolean validate) {
        return segmentService.removeScoringDefinition(scoringId, validate);
    }

    /**
     * Retrieves the list of Segment and Scoring metadata depending on the specified scoring.
     * A segment or scoring is depending on a segment if it includes a scoringCondition with a test on this scoring.
     *
     * @param scoringId the segment identifier
     * @return a list of Segment/Scoring Metadata depending on the specified scoring
     */
    @GET
    @Path("/{scoringID}/impacted")
    public DependentMetadata getScoringDependentMetadata(@PathParam("scoringID") String scoringId) {
        return segmentService.getScoringDependentMetadata(scoringId);
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
