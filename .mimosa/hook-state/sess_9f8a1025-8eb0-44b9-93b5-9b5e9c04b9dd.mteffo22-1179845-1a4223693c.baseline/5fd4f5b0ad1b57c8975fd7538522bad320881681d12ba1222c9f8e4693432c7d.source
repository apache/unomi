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
     * @api.status 200 array org.apache.unomi.api.Metadata Goal metadata for all stored goals (may be empty).
     * @api.example [{"id":"checkout-goal","name":"Checkout completed","scope":"mysite","enabled":true}]
     */
    @GET
    @Path("/")
    public Set<Metadata> getGoalMetadatas() {
        return goalsService.getGoalMetadatas();
    }

    /**
     * Saves the specified goal in the context server and creates associated {@link Rule}s if the goal is enabled.
     * Body is a full {@link Goal}: {@code metadata}, optional {@code startEvent} and {@code targetEvent}
     * (each a condition with JSON field {@code type} + {@code parameterValues}), and optional {@code campaignId}.
     *
     * @param goal the Goal to be saved
     * @api.status 204 empty Goal created or updated.
     * @api.status 400 empty Invalid or unreadable goal body.
     * @api.example {"itemId":"checkout-goal","itemType":"goal","metadata":{"id":"checkout-goal","name":"Checkout completed","scope":"mysite","enabled":true},"startEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"targetEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"purchase"}}}
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
     * @api.status 200 array org.apache.unomi.api.Metadata Matching goal metadata (may be empty).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example [{"id":"checkout-goal","name":"Checkout completed","scope":"mysite","enabled":true}]
     */
    @POST
    @Path("/query")
    public Set<Metadata> getGoalMetadatas(Query query) {
        return goalsService.getGoalMetadatas(query);
    }

    /**
     * Returns the goal with the given ID.
     * When the goal does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param goalId the goal identifier
     * @return the goal, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.goals.Goal Goal found, or empty body when missing.
     * @api.example {"itemId":"checkout-goal","itemType":"goal","metadata":{"id":"checkout-goal","name":"Checkout completed","scope":"mysite","enabled":true},"startEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"targetEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"purchase"}}}
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
     * @api.status 204 empty Goal deleted.
     * @api.example {"itemId":"checkout-goal","itemType":"goal","metadata":{"id":"checkout-goal","name":"Checkout completed","scope":"mysite","enabled":true},"startEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"view"}},"targetEvent":{"type":"eventTypeCondition","parameterValues":{"eventTypeId":"purchase"}}}
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
     * @api.status 200 org.apache.unomi.api.goals.GoalReport Goal report with global and split statistics.
     * @api.status 404 empty Goal not found.
     * @api.example {"globalStats":{"key":"global","startCount":1000,"targetCount":120,"conversionRate":0.12,"percentage":100.0},"split":[{"key":"variant-a","startCount":500,"targetCount":70,"conversionRate":0.14,"percentage":50.0}]}
     */
    @GET
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId) {
        if (goalsService.getGoal(goalId) == null) {
            throw new NotFoundException("Goal not found: " + goalId);
        }
        return goalsService.getGoalReport(goalId);
    }

    /**
     * Returns a filtered goal report for the given goal and aggregate query.
     * The aggregate query limits which report elements are included in the response.
     *
     * @param goalId the goal identifier
     * @param query the aggregate query that limits report elements
     * @return the filtered goal report
     * @api.status 200 org.apache.unomi.api.goals.GoalReport Filtered goal report.
     * @api.status 400 empty Invalid or unreadable aggregate query body.
     * @api.status 404 empty Goal not found.
     * @api.example {"globalStats":{"key":"global","startCount":1000,"targetCount":120,"conversionRate":0.12,"percentage":100.0},"split":[{"key":"variant-a","startCount":500,"targetCount":70,"conversionRate":0.14,"percentage":50.0}]}
     */
    @POST
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId, AggregateQuery query) {
        if (goalsService.getGoal(goalId) == null) {
            throw new NotFoundException("Goal not found: " + goalId);
        }
        return goalsService.getGoalReport(goalId, query);
    }
}
