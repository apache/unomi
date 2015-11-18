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
import org.apache.unomi.api.services.GoalsService;

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
