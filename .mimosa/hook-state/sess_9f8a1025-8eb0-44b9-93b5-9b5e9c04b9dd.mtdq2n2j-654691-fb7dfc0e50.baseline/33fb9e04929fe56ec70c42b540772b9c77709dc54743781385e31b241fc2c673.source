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
     * Returns profiles that currently match the segment with the given ID.
     *
     * @param segmentId the segment identifier
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of matching profiles
     * @api.status 200 org.apache.unomi.api.PartialList Profiles page (list items are Profile).
     * @api.example {"list":[{"itemId":"profile-1","itemType":"profile","properties":{"isPremium":true}}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/{segmentID}/match")
    public PartialList<Profile> getMatchingIndividuals(@PathParam("segmentID") String segmentId, @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getMatchingIndividuals(segmentId, offset, size, sortBy);
    }

    /**
     * Returns how many profiles currently match the segment with the given ID.
     *
     * @param segmentId the segment identifier
     * @return the number of matching profiles
     * @api.status 200 empty Match count as a JSON number.
     * @api.example 42
     */
    @GET
    @Path("/{segmentID}/count")
    public long getMatchingIndividualsCount(@PathParam("segmentID") String segmentId) {
        return segmentService.getMatchingIndividualsCount(segmentId);
    }

    /**
     * Determines whether the specified profile is a member of the given segment.
     * <p>
     * The {@code profile} path variable is the profile identifier (the JAX-RS type is {@link Profile} for binding).
     *
     * @param profile   the profile to check (bound from the profile id path segment)
     * @param segmentId the identifier of the segment against which we want to check the profile
     * @return {@code true} if the specified profile is in the specified segment, {@code false} otherwise
     * @api.status 200 empty Membership flag.
     * @api.example true
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
     * @api.status 200 array org.apache.unomi.api.Metadata Segment metadata for the requested page (may be empty).
     * @api.example [{"id":"premium-profiles","name":"Premium profiles","scope":"mysite","enabled":true}]
     */
    @GET
    @Path("/")
    public List<Metadata> getSegmentMetadatas(@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return segmentService.getSegmentMetadatas(offset, size, sortBy).getList();
    }

    /**
     * Returns segment and scoring metadata that depend on the given segment.
     * <p>
     * A dependent definition includes a profile-segment condition that references this segment
     * (useful before delete or when editing related definitions).
     *
     * @param segmentId the segment identifier
     * @return metadata for dependent segments and scorings
     * @api.status 200 org.apache.unomi.api.segments.DependentMetadata Dependent segments/scorings (lists may be empty).
     * @api.example {"segments":[],"scorings":[]}
     */
    @GET
    @Path("/{segmentID}/impacted")
    public DependentMetadata getSegmentDependentMetadata(@PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDependentMetadata(segmentId);
    }

    /**
     * Persists the specified segment in the context server.
     * Body is a full {@link Segment}: {@code metadata} plus a membership {@code condition}
     * (JSON field {@code type} + {@code parameterValues}).
     *
     * @param segment the segment to be persisted
     * @api.status 204 empty Segment created or updated.
     * @api.status 400 empty Invalid or unreadable segment body (condition schema / validation).
     * @api.example {"itemId":"premium-profiles","itemType":"segment","metadata":{"id":"premium-profiles","name":"Premium profiles","scope":"mysite","enabled":true,"description":"Profiles marked as premium"},"condition":{"type":"profilePropertyCondition","parameterValues":{"propertyName":"properties.isPremium","comparisonOperator":"equals","propertyValue":"true"}}}
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
     * @api.status 200 org.apache.unomi.api.PartialList Metadata page (list items are Metadata).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"id":"premium-profiles","name":"Premium profiles","scope":"mysite","enabled":true}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/query")
    public PartialList<Metadata> getListMetadatas(Query query) {
        return segmentService.getSegmentMetadatas(query);
    }

    /**
     * Returns the segment definition with the given ID.
     * When the segment does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param segmentId the segment identifier
     * @return the segment, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.segments.Segment Segment found, or empty body when missing.
     * @api.example {"itemId":"premium-profiles","itemType":"segment","metadata":{"id":"premium-profiles","name":"Premium profiles","scope":"mysite","enabled":true,"description":"Profiles marked as premium"},"condition":{"type":"profilePropertyCondition","parameterValues":{"propertyName":"properties.isPremium","comparisonOperator":"equals","propertyValue":"true"}}}
     */
    @GET
    @Path("/{segmentID}")
    public Segment getSegmentDefinition(@PathParam("segmentID") String segmentId) {
        return segmentService.getSegmentDefinition(segmentId);
    }

    /**
     * Removes the segment definition identified by the specified identifier.
     * <p>
     * When {@code validate} is {@code true}, dependents are checked first: if any segment or scoring
     * references this segment, it is <strong>not</strong> deleted and the impacted metadata is returned.
     * When {@code validate} is {@code false}, the segment is deleted without that check.
     *
     * @param segmentId the identifier of the segment we want to delete
     * @param validate  whether or not to perform dependency validation before delete
     * @return dependent metadata when delete was blocked; empty lists when deleted or nothing depended on it
     * @api.status 200 org.apache.unomi.api.segments.DependentMetadata Empty lists when deleted; otherwise dependents that blocked delete.
     * @api.example {"segments":[{"id":"premium-buyers","name":"Premium buyers","scope":"mysite","enabled":true}],"scorings":[]}
     */
    @DELETE
    @Path("/{segmentID}")
    public DependentMetadata removeSegmentDefinition(@PathParam("segmentID") String segmentId, @QueryParam("validate") boolean validate) {
        return segmentService.removeSegmentDefinition(segmentId, validate);
    }

    /**
     * Deprecated maintenance endpoint kept for backward compatibility.
     * Reloads in-memory segments from storage by re-saving each definition.
     *
     * @deprecated As of version 1.1.0-incubating, not needed anymore
     * @api.status 204 empty In-memory segments re-persisted.
     * @api.example {}
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
