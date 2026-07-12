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
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.goals.GoalReport;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.GoalsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * A JAX-RS endpoint to manage {@link Goal}s and related information.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/goals")
@Component(service=GoalsServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class GoalsServiceEndPoint {

    @Reference
    private GoalsService goalsService;

    /**
     * Sets the goals service.
     *
     * @param goalsService the goals service
     */
    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    /**
     * Returns metadata for all goals.
     *
     * @return goal metadata for every stored goal
     */
    @GET
    @Path("/")
    public Set<Metadata> getGoalMetadatas() {
        return goalsService.getGoalMetadatas();
    }

    /**
     * Saves the specified goal in the context server and creates associated {@link Rule}s if the goal is enabled.
     *
     * @param goal the Goal to be saved
     */
    @POST
    @Path("/")
    public void setGoal(Goal goal) {
        goalsService.setGoal(goal);
    }

    /**
     * Returns goal metadata matching the given query.
     *
     * @param query the query used to filter goals
     * @return metadata for goals that match the query
     */
    @POST
    @Path("/query")
    public Set<Metadata> getGoalMetadatas(Query query) {
        return goalsService.getGoalMetadatas(query);
    }

    /**
     * Returns the goal with the given ID.
     *
     * @param goalId the goal identifier
     * @return the goal, or {@code null} when it does not exist
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
     * Returns the performance report for the goal with the given ID.
     *
     * @param goalId the goal identifier
     * @return the goal report
     */
    @GET
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId) {
        return goalsService.getGoalReport(goalId);
    }

    /**
     * Returns a filtered goal report for the given goal and aggregate query.
     *
     * @param goalId the goal identifier
     * @param query the aggregate query that limits report elements
     * @return the filtered goal report
     */
    @POST
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId, AggregateQuery query) {
        return goalsService.getGoalReport(goalId, query);
    }
}
