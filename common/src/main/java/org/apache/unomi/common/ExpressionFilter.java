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
package org.apache.unomi.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class ExpressionFilter {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionFilter.class.getName());

    Set<Pattern> allowedExpressionPatterns = null;
    Set<Pattern> forbiddenExpressionPatterns = null;

    private static Set<Pattern> defaultAllowedExpressionPatterns = null;
    private static Set<Pattern> defaultForbiddenExpressionPatterns = null;

    static {
        String systemAllowedPatterns = System.getProperty("org.apache.unomi.scripting.filter.allow", "all");
        if (systemAllowedPatterns != null) {
            if ("all".equals(systemAllowedPatterns.trim())) {
                defaultAllowedExpressionPatterns = null;
            } else {
                if (systemAllowedPatterns.trim().length() > 0) {
                    String[] systemAllowedPatternParts = systemAllowedPatterns.split(",");
                    defaultAllowedExpressionPatterns = new HashSet<>();
                    for (String systemAllowedPatternPart : systemAllowedPatternParts) {
                        defaultAllowedExpressionPatterns.add(Pattern.compile(systemAllowedPatternPart));
                    }
                } else {
                    defaultAllowedExpressionPatterns = null;
                }
            }
        }

        String systemForbiddenPatterns = System.getProperty("org.apache.unomi.scripting.filter.forbid", ".*Runtime.*,.*ProcessBuilder.*,.*exec.*,.*invoke.*,.*getClass.*,.*Class.*,.*ClassLoader.*,.*System.*,.*Method.*,.*method.*,.*Compiler.*,.*Thread.*,.*FileWriter.*,.*forName.*,.*Socket.*,.*DriverManager.*");
        if (systemForbiddenPatterns != null) {
            if (systemForbiddenPatterns.trim().length() > 0) {
                String[] systemForbiddenPatternParts = systemForbiddenPatterns.split(",");
                defaultForbiddenExpressionPatterns = new HashSet<>();
                for (String systemForbiddenPatternPart : systemForbiddenPatternParts) {
                    defaultForbiddenExpressionPatterns.add(Pattern.compile(systemForbiddenPatternPart));
                }
            } else {
                defaultForbiddenExpressionPatterns = null;
            }
        }
    }

    public ExpressionFilter() {
        allowedExpressionPatterns = defaultAllowedExpressionPatterns;
        forbiddenExpressionPatterns = defaultForbiddenExpressionPatterns;
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
