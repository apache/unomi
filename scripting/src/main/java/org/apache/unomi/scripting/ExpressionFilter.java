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
package org.apache.unomi.scripting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * An expression filter is used to allow/deny scripts for execution.
 */
public class ExpressionFilter {
    private static final Logger logger = LoggerFactory.getLogger(ExpressionFilter.class.getName());

    private final Set<Pattern> allowedExpressionPatterns;
    private final Set<Pattern> forbiddenExpressionPatterns;

    public ExpressionFilter(Set<Pattern> allowedExpressionPatterns, Set<Pattern> forbiddenExpressionPatterns) {
        this.allowedExpressionPatterns = allowedExpressionPatterns;
        this.forbiddenExpressionPatterns = forbiddenExpressionPatterns;
    }

    public String filter(String expression) {
        if (forbiddenExpressionPatterns != null && expressionMatches(expression, forbiddenExpressionPatterns)) {
            logger.warn("Expression {} is forbidden by expression filter", expression);
            return null;
        }
        if (allowedExpressionPatterns != null && !expressionMatches(expression, allowedExpressionPatterns)) {
            logger.warn("Expression {} is not allowed by expression filter", expression);
            return null;
        }
        return expression;
    }

    private boolean expressionMatches(String expression, Set<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(expression).matches()) {
                return true;
            }
        }
        return false;
    }
}
