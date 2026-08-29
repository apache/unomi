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
package org.apache.unomi.services.impl.validation.validators;

import org.apache.unomi.api.services.ValueTypeValidator;
import org.apache.unomi.persistence.spi.conditions.DateUtils;
/**
 * Date value type validator.
 */
public class DateValueTypeValidator implements ValueTypeValidator {
    @Override
    public String getValueTypeId() {
        return "date";
    }

    @Override
    public boolean validate(Object value) {
        if (value == null) {
            return true;
        }
        // Condition parameters of type "date" are almost always plain ISO-8601 strings (from JSON
        // condition definitions or persisted event/profile properties), not already-parsed Date
        // objects, so delegate to the same parser used at actual condition-evaluation time
        // (e.g. PropertyConditionEvaluator) rather than only accepting typed date/time instances.
        return DateUtils.getDate(value) != null;
    }

    @Override
    public String getValueTypeDescription() {
        return "Value must be a date (Date, OffsetDateTime, ZonedDateTime, LocalDateTime, Instant, "
                + "or a parseable ISO-8601 / epoch-millis string)";
    }
}
