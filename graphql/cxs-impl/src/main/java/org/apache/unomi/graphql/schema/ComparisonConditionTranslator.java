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
package org.apache.unomi.graphql.schema;

public class ComparisonConditionTranslator {

    public static String translateComparisonCondition(final String originalCondition) {
        if ("lt".equals(originalCondition)) {
            return "lessThan";
        } else if ("lte".equals(originalCondition)) {
            return "lessThanOrEqualTo";
        } else if ("gt".equals(originalCondition)) {
            return "greaterThan";
        } else if ("gte".equals(originalCondition)) {
            return "greaterThanOrEqualTo";
        } else if ("regexp".equals(originalCondition)) {
            return "matchesRegex";
        } else if ("distance".equals(originalCondition)) {
            return "between";
        } else {
            return originalCondition;
        }
    }

}
