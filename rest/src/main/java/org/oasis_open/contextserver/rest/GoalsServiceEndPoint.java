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
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.goals.GoalReport;
import org.oasis_open.contextserver.api.query.AggregateQuery;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.services.GoalsService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Goal}s and related information.
 */
@Path("/goals")
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class GoalsServiceEndPoint {

    private GoalsService goalsService;

    @WebMethod(exclude = true)
    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    /**
     * Retrieves the set of Metadata associated with existing goals.
     *
     * @return the set of Metadata associated with existing goals
     */
    @GET
    @Path("/")
    public Set<Metadata> getGoalMetadatas() {
        return goalsService.getGoalMetadatas();
    }


    /**
     * Saves the specified goal in the context server and creates associated {@link org.oasis_open.contextserver.api.rules.Rule}s if the goal is enabled.
     *
     * @param goal the Goal to be saved
     */
    @POST
    @Path("/")
    public void setGoal(Goal goal) {
        goalsService.setGoal(goal);
    }

    /**
     * Retrieves the set of Metadata associated with existing goals matching the specified {@link Query}
     *
     * @param query the Query used to filter the Goals which metadata we want to retrieve
     * @return the set of Metadata associated with existing goals matching the specified {@link Query}
     */
    @POST
    @Path("/query")
    public Set<Metadata> getGoalMetadatas(Query query) {
        return goalsService.getGoalMetadatas(query);
    }

    /**
     * Retrieves the goal associated with the specified identifier.
     *
     * @param goalId the identifier of the goal to retrieve
     * @return the goal associated with the specified identifier or {@code null} if no such goal exists
     */
    @GET
    @Path("/{goalId}")
    public Goal getGoal(@PathParam("goalId") String goalId) {
        return goalsService.getGoal(goalId);
    }

    /**
     * Removes the goal associated with the specified identifier, also removing associated rules if needed.
     *
     * @param goalId the identifier of the goal to be removed
     */
    @DELETE
    @Path("/{goalId}")
    public void removeGoal(@PathParam("goalId") String goalId) {
        goalsService.removeGoal(goalId);
    }

    /**
     * Retrieves the report for the goal identified with the specified identifier.
     *
     * @param goalId the identifier of the goal which report we want to retrieve
     * @return the report for the specified goal
     */
    @GET
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId) {
        return goalsService.getGoalReport(goalId);
    }

    /**
     * Retrieves the report for the goal identified with the specified identifier, considering only elements determined by the specified {@link AggregateQuery}.
     *
     * @param goalId the identifier of the goal which report we want to retrieve
     * @param query  an {@link AggregateQuery} to further specify which elements of the report we want
     * @return the report for the specified goal and query
     */
    @POST
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId, AggregateQuery query) {
        return goalsService.getGoalReport(goalId, query);
    }
}
