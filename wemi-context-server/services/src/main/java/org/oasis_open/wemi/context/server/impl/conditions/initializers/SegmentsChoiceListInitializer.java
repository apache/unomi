package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class SegmentsChoiceListInitializer implements ChoiceListInitializer {

    SegmentService segmentService;

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        Set<Metadata> userProperties = segmentService.getSegmentMetadatas();
        for (Metadata userProperty : userProperties) {
            choiceListValues.add(new ChoiceListValue(userProperty.getId(), userProperty.getName()));
        }
        return choiceListValues;
    }
}
