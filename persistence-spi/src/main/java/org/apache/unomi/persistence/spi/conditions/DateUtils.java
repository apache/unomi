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
package org.apache.unomi.persistence.spi.conditions;

import org.apache.unomi.persistence.spi.conditions.datemath.DateMathParseException;
import org.apache.unomi.persistence.spi.conditions.datemath.DateMathParser;
import org.apache.unomi.persistence.spi.conditions.datemath.JavaDateFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtils.class.getName());

    public static Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        } else {
            JavaDateFormatter formatter = new JavaDateFormatter("strict_date_optional_time||epoch_millis");
            DateMathParser dateMathParser = new DateMathParser(formatter, DateTimeFormatter.ISO_DATE_TIME);
            try {
                Instant instant = dateMathParser.parse(value.toString(), System::currentTimeMillis, false, ZoneOffset.UTC);
                return Date.from(instant);
            } catch (DateMathParseException e) {
                LOGGER.warn("unable to parse date. See debug log level for full stacktrace");
                LOGGER.debug("unable to parse date {}", value, e);
            }
            return null;
        }
    }

}
