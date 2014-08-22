package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.goals.Goal;
import org.oasis_open.wemi.context.server.api.goals.GoalReport;
import org.oasis_open.wemi.context.server.api.services.GoalsService;

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
public class GoalsServiceEndPoint implements GoalsService {

    GoalsService goalsService;

    @WebMethod(exclude = true)
    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    @GET
    @Path("/")
    public Set<Metadata> getGoalMetadatas() {
        return goalsService.getGoalMetadatas();
    }

    @GET
    @Path("/{goalId}")
    public Goal getGoal(@PathParam("goalId") String goalId) {
        return goalsService.getGoal(goalId);
    }

    @POST
    @Path("/{goalId}")
    public void setGoal(@PathParam("goalId") String goalId, Goal goal) {
        goalsService.setGoal(goalId, goal);
    }

    @PUT
    @Path("/{goalId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createGoal(@PathParam("goalId") String goalId, @FormParam("goalName") String name, @FormParam("goalDescription") String description) {
        goalsService.createGoal(goalId, name, description);
    }

    @DELETE
    @Path("/{goalId}")
    public void removeGoal(@PathParam("goalId") String goalId) {
        goalsService.removeGoal(goalId);
    }

    @GET
    @Path("/{goalID}/report")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId) {
        return goalsService.getGoalReport(goalId);
    }

    @GET
    @Path("/{goalID}/report/{split}")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId, @PathParam("split") String split) {
        return goalsService.getGoalReport(goalId, split);
    }

    @POST
    @Path("/{goalID}/conditionalReport/{split}")
    public GoalReport getGoalReport(@PathParam("goalID") String goalId, @PathParam("split") String split, Condition condition) {
        return goalsService.getGoalReport(goalId, split, condition);
    }

}
