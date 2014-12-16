package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.goals.Goal;
import org.oasis_open.wemi.context.server.api.goals.GoalReport;

import java.util.Set;

public interface GoalsService {
    Set<Metadata> getGoalMetadatas();

    Set<Metadata> getGoalMetadatas(String scope);

    Goal getGoal(String scope, String goalId);

    void setGoal(Goal goal);

    void createGoal(String scope, String goalId, String name, String description);

    void removeGoal(String scope, String goalId);

    GoalReport getGoalReport(String scope, String goalId);

    GoalReport getGoalReport(String scope, String goalId, String split);

    GoalReport getGoalReport(String scope, String goalId, String split, Condition filter);
}
