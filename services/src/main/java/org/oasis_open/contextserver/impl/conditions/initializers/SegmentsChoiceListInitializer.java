package org.oasis_open.contextserver.impl.conditions.initializers;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.SegmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Initializer for segment choice list.
 */
public class SegmentsChoiceListInitializer implements ChoiceListInitializer {

    SegmentService segmentService;

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        List<Metadata> profileProperties = segmentService.getSegmentMetadatas(0, 50, null).getList();
        for (Metadata profileProperty : profileProperties) {
            choiceListValues.add(new ChoiceListValue(profileProperty.getId(), profileProperty.getName()));
        }
        return choiceListValues;
    }
}
