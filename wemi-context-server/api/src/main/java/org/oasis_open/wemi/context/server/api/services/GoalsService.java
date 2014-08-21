package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.goals.Goal;

import java.util.Set;

public interface GoalsService {
    Set<Metadata> getSegmentMetadatas();

    Goal getGoal(String goalId);

    void setGoal(String goalId, Goal goal);

    void createGoal(String goalId,  String name,String description);

    void removeGoal( String goalId) ;

    float getGoalSuccessRate(String goalId);
}
