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
package org.apache.unomi.shell.dev.commands;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for common command functionality.
 */
public final class CommandUtils {

    /**
     * Standard date format used across commands: "yyyy-MM-dd HH:mm:ss"
     */
    public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * Thread-local SimpleDateFormat instance for date formatting.
     * SimpleDateFormat is not thread-safe, so we use ThreadLocal to ensure thread safety.
     */
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(DATE_FORMAT_PATTERN));

    private CommandUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Format a date using the standard date format pattern.
     * 
     * @param date the date to format
     * @return the formatted date string, or "-" if date is null
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        return DATE_FORMAT.get().format(date);
    }

    /**
     * Format a date using the standard date format pattern.
     * 
     * @param date the date to format
     * @param nullValue the value to return if date is null
     * @return the formatted date string, or nullValue if date is null
     */
    public static String formatDate(Date date, String nullValue) {
        if (date == null) {
            return nullValue;
        }
        return DATE_FORMAT.get().format(date);
    }

    /**
     * Get a SimpleDateFormat instance for the standard pattern.
     * Note: This returns a new instance each time. For thread-safe usage,
     * prefer using formatDate() methods.
     * 
     * @return a SimpleDateFormat instance
     */
    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT_PATTERN);
    }
}
