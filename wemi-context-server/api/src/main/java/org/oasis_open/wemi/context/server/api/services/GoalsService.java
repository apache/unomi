package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.goals.Goal;

import java.util.Set;

public interface GoalsService {
    Set<Goal> getGoals();

    float getGoalSuccessRate(String goalId);
}
