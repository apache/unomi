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
package org.apache.unomi.graphql.utils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

public final class DateUtils {

    private DateUtils() {
        throw new AssertionError();
    }

    public static OffsetDateTime toOffsetDateTime(final Date date) {
        if (date == null) {
            return null;
        }

        return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public static LocalDate toLocalDate(final Date date) {
        if (date == null) {
            return null;
        }

        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Date toDate(final OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }

        return new Date(offsetDateTime.toInstant().toEpochMilli());
    }

    @SuppressWarnings("unchecked")
    public static OffsetDateTime offsetDateTimeFromMap(final Map<String, Object> parameterValues) {
        if (parameterValues == null) {
            return null;
        }

        final Map<String, Object> offsetAsMap = (Map<String, Object>) parameterValues.get("offset");

        final ZoneOffset zoneOffset = ZoneOffset.of(offsetAsMap.get("id").toString());

        return OffsetDateTime.of(
                (int) parameterValues.get("year"),
                (int) parameterValues.get("monthValue"),
                (int) parameterValues.get("dayOfMonth"),
                (int) parameterValues.get("hour"),
                (int) parameterValues.get("minute"),
                (int) parameterValues.get("second"),
                (int) parameterValues.get("nano"),
                zoneOffset);

    }

}
