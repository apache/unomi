package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.goals.GoalReport;

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
