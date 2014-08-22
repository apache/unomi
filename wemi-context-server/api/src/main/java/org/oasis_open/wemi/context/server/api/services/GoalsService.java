package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.goals.Goal;
import org.oasis_open.wemi.context.server.api.goals.GoalReport;

import java.util.Set;

public interface GoalsService {
    Set<Metadata> getGoalMetadatas();

    Goal getGoal(String goalId);

    void setGoal(String goalId, Goal goal);

    void createGoal(String goalId, String name, String description);

    void removeGoal(String goalId);

    GoalReport getGoalReport(String goalId);

    GoalReport getGoalReport(String goalId, String split);

    GoalReport getGoalReport(String goalId, String split, Condition filter);
}
