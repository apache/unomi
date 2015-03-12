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

import java.util.ArrayList;
import java.util.List;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;

/**
 * Initializer for the set of available comparison operators.
 */
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer, I18nSupport {

    private static final List<ChoiceListValue> OPERATORS;

    static {
        OPERATORS = new ArrayList<ChoiceListValue>(12);
        OPERATORS.add(new ComparisonOperatorChoiceListValue("equals", "comparisonOperator.equals"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notEquals", "comparisonOperator.notEquals"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThan", "comparisonOperator.lessThan", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThan", "comparisonOperator.greaterThan", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThanOrEqualTo", "comparisonOperator.lessThanOrEqualTo",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThanOrEqualTo", "comparisonOperator.greaterThanOrEqualTo",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("between", "comparisonOperator.between",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("startsWith", "comparisonOperator.startsWith", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("endsWith", "comparisonOperator.endsWith", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("matchesRegex", "comparisonOperator.matchesRegularExpression",
                "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("contains", "comparisonOperator.contains", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("exists", "comparisonOperator.exists"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("missing", "comparisonOperator.missing"));
        
        OPERATORS.add(new ComparisonOperatorChoiceListValue("in", "comparisonOperator.in"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notIn", "comparisonOperator.notIn"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("all", "comparisonOperator.all"));
    }

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        return OPERATORS;
    }
}
