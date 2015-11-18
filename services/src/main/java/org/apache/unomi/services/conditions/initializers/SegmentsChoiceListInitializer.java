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

package org.apache.unomi.services.conditions.initializers;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.conditions.initializers.ChoiceListInitializer;
import org.apache.unomi.api.conditions.initializers.ChoiceListValue;
import org.apache.unomi.api.services.SegmentService;

import java.util.ArrayList;
import java.util.List;

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
