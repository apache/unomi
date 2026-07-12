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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.services.SegmentService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * JAX-RS endpoint for managing {@link Scoring} definitions and their dependents.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/scoring")
@Component(service=ScoringServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class ScoringServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoringServiceEndPoint.class.getName());

    @Reference
    private SegmentService segmentService;

    /**
     * Creates the scoring service endpoint.
     */
    public ScoringServiceEndPoint() {
        LOGGER.info("Initializing scoring service endpoint...");
    }

    /**
     * Sets the segment service.
     *
     * @param segmentService the segment service
     */
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    /**
     * Returns scoring metadata with paging and optional sorting.
     *
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return matching scoring metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getScoringMetadatas(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getScoringMetadatas(offset,size,sortBy).getList();
    }

    /**
     * Returns scoring metadata matching the given query.
     *
     * @param query the query scorings must match
     * @return a paged list of matching scoring metadata
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getScoringMetadatas(Query query) {
        return segmentService.getScoringMetadatas(query);
    }

    /**
     * Returns the scoring definition with the given ID.
     *
     * @param scoringId the scoring identifier
     * @return the scoring, or {@code null} when it does not exist
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
     * Returns segment and scoring metadata that depend on the given scoring.
     * <p>
     * A dependent definition includes a scoring condition that references this scoring.
     *
     * @param scoringId the scoring identifier
     * @return metadata for dependent segments and scorings
     */
    @GET
    @Path("/{scoringID}/impacted")
    public DependentMetadata getScoringDependentMetadata(@PathParam("scoringID") String scoringId) {
        return segmentService.getScoringDependentMetadata(scoringId);
    }

    /**
     * Deprecated maintenance endpoint kept for backward compatibility.
     *
     * @deprecated As of version 1.1.0-incubating, not needed anymore
     */
    @Deprecated
    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : segmentService.getScoringMetadatas(0, 50, null).getList()) {
            Scoring s = segmentService.getScoringDefinition(metadata.getId());
            segmentService.setScoringDefinition(s);
        }
    }

}
