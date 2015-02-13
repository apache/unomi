package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.GoalsService;

import java.util.ArrayList;
import java.util.List;

public class GoalsChoiceListInitializer implements ChoiceListInitializer {

    private GoalsService goalsService;

    public void setGoalsService(GoalsService goalsService) {
        this.goalsService = goalsService;
    }

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> r = new ArrayList<>();
        for (Metadata metadata : goalsService.getGoalMetadatas()) {
            r.add(new ChoiceListValue(metadata.getId(), metadata.getName()));
        }
        return r;
    }
}
