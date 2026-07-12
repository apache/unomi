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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.SegmentService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * A JAX-RS endpoint to manage {@link Segment}s.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/segments")
@Component(service=SegmentServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class SegmentServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentServiceEndPoint.class.getName());

    @Reference
    private SegmentService segmentService;

    /**
     * Creates the segment service endpoint.
     */
        public SegmentServiceEndPoint() {
        LOGGER.info("Initializing segment service endpoint...");
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
     * Returns profiles that match the segment with the given ID.
     *
     * @param segmentId the segment identifier
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of matching profiles
     */
    @GET
    @Path("/{segmentID}/match")
    public PartialList<Profile> getMatchingIndividuals(@PathParam("segmentID") String segmentId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getMatchingIndividuals(segmentId, offset, size, sortBy);
    }

    /**
     * Returns how many profiles match the segment with the given ID.
     *
     * @param segmentId the segment identifier
     * @return the number of matching profiles
     */
    @GET
    @Path("/{segmentID}/count")
    public long getMatchingIndividualsCount(@PathParam("segmentID") String segmentId) {
        return segmentService.getMatchingIndividualsCount(segmentId);
    }

    /**
     * Determines whether the specified profile is part of the segment identified by the specified identifier.
     *
     * @param profile   the profile we want to check
     * @param segmentId the identifier of the segment against which we want to check the profile
     * @return {@code true} if the specified profile is in the specified segment, {@code false} otherwise
     */
    @GET
    @Path("/{segmentID}/match/{profile}")
    public Boolean isProfileInSegment(@PathParam("profile") Profile profile, @PathParam("segmentID") String segmentId) {
        return segmentService.isProfileInSegment(profile, segmentId);
    }

    /**
     * Returns segment metadata with paging and optional sorting.
     *
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return matching segment metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getSegmentMetadatas(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getSegmentMetadatas(offset, size, sortBy).getList();
    }

    /**
     * Returns segment and scoring metadata that depend on the given segment.
     * <p>
     * A dependent definition includes a profile-segment condition that references this segment.
     *
     * @param segmentId the segment identifier
     * @return metadata for dependent segments and scorings
     */
    @GET
    @Path("/{segmentID}/impacted")
    public DependentMetadata getSegmentDependentMetadata(@PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDependentMetadata(segmentId);
    }

    /**
     * Persists the specified segment in the context server.
     *
     * @param segment the segment to be persisted
     */
    @POST
    @Path("/")
    public void setSegmentDefinition(Segment segment) {
        segmentService.setSegmentDefinition(segment);
    }

    /**
     * Returns segment metadata matching the given query.
     *
     * @param query the query segments must match
     * @return a paged list of matching segment metadata
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getListMetadatas(Query query) {
        return segmentService.getSegmentMetadatas(query);
    }

    /**
     * Returns the segment definition with the given ID.
     *
     * @param segmentId the segment identifier
     * @return the segment, or {@code null} when it does not exist
     */
    @GET
    @Path("/{segmentID}")
    public Segment getSegmentDefinition(@PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDefinition(segmentId);
    }

    /**
     * Removes the segment definition identified by the specified identifier. We can specify that we want the operation to be validated beforehand so that we can
     * know if any other segment that might use the segment we're trying to delete as a condition might be impacted. If {@code validate} is set to {@code false}, no
     * validation is performed. If set to {@code true}, we will first check if any segment or scoring depends on the segment we're trying to delete and if so we will not delete the
     * segment but rather return the list of the metadata of the impacted items. If no dependents are found, then we properly delete the segment.
     *
     * @param segmentId the identifier of the segment we want to delete
     * @param validate  whether or not to perform validation
     * @return a list of impacted segment metadata if any or an empty list if none were found or validation was skipped
     */
    @DELETE
    @Path("/{segmentID}")
    public DependentMetadata removeSegmentDefinition(@PathParam("segmentID") String segmentId, @QueryParam("validate") boolean validate) {
        return segmentService.removeSegmentDefinition(segmentId, validate);
    }

    /**
     * Deprecated maintenance endpoint kept for backward compatibility.     *
     * @deprecated As of version 1.1.0-incubating, not needed anymore
     */
    @Deprecated
    @GET
    @Path("/resetQueries")
    public void resetQueries() {
        for (Metadata metadata : segmentService.getSegmentMetadatas(0, 50, null).getList()) {
            Segment s = segmentService.getSegmentDefinition(metadata.getId());
            segmentService.setSegmentDefinition(s);
        }
    }

}
