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

import org.apache.unomi.api.conditions.initializers.ChoiceListInitializer;
import org.apache.unomi.api.conditions.initializers.ChoiceListValue;
import org.apache.unomi.api.conditions.initializers.I18nSupport;
import org.osgi.framework.BundleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializer for the set of available comparison operators.
 */
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer, I18nSupport {

    private List<ChoiceListValue> operators;

    private BundleContext bundleContext;

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        return operators;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;

        operators = new ArrayList<>(12);
        operators.add(new ComparisonOperatorChoiceListValue("equals", "comparisonOperator.equals",
                "string", "integer", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("notEquals", "comparisonOperator.notEquals",
                "string", "integer", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("lessThan", "comparisonOperator.lessThan", "integer", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("greaterThan", "comparisonOperator.greaterThan", "integer", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("lessThanOrEqualTo", "comparisonOperator.lessThanOrEqualTo",
                "integer", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("greaterThanOrEqualTo", "comparisonOperator.greaterThanOrEqualTo",
                "integer", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("between", "comparisonOperator.between",
                "integer", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("startsWith", "comparisonOperator.startsWith", "string", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("endsWith", "comparisonOperator.endsWith", "string", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("matchesRegex", "comparisonOperator.matchesRegularExpression",
                "string", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("contains", "comparisonOperator.contains", "string", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("exists", "comparisonOperator.exists"));
        operators.add(new ComparisonOperatorChoiceListValue("missing", "comparisonOperator.missing"));
        operators.add(new ComparisonOperatorChoiceListValue("in", "comparisonOperator.in", "string", "integer", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("notIn", "comparisonOperator.notIn", "string", "integer", "email"));
        operators.add(new ComparisonOperatorChoiceListValue("all", "comparisonOperator.all"));
        operators.add(new ComparisonOperatorChoiceListValue("isDay", "comparisonOperator.isDay", "date"));
        operators.add(new ComparisonOperatorChoiceListValue("isNotDay", "comparisonOperator.isNotDay", "date"));

        for (ChoiceListValue op : operators) {
            ((ComparisonOperatorChoiceListValue) op).setPluginId(bundleContext.getBundle().getBundleId());
        }

    }
}
