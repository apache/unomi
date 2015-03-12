package org.oasis_open.contextserver.persistence.spi.aggregate;

/*
 * #%L
 * context-server-persistence-spi
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

public class DateAggregate extends BaseAggregate{
    private static final String DEFAULT_INTERVAL = "1M";
    public static final DateAggregate SECOND = new DateAggregate("1s");
    public static final DateAggregate MINUTE = new DateAggregate("1m");
    public static final DateAggregate HOUR = new DateAggregate("1h");
    public static final DateAggregate DAY = new DateAggregate("1d");
    public static final DateAggregate WEEK = new DateAggregate("1w");
    public static final DateAggregate MONTH = new DateAggregate("1M");
    public static final DateAggregate QUARTER = new DateAggregate("1q");
    public static final DateAggregate YEAR = new DateAggregate("1y");
    public static DateAggregate seconds(int sec) {
        return new DateAggregate(sec + "s");
    }
    public static DateAggregate minutes(int min) {
        return new DateAggregate(min + "m");
    }
    public static DateAggregate hours(int hours) {
        return new DateAggregate(hours + "h");
    }
    public static DateAggregate days(int days) {
        return new DateAggregate(days + "d");
    }
    public static DateAggregate weeks(int weeks) {
        return new DateAggregate(weeks + "w");
    }

    private String interval;

    private String format;

    public DateAggregate(String field) {
        super(field);
        this.interval = DEFAULT_INTERVAL;
    }

    public DateAggregate(String field, String interval) {
        super(field);
        this.interval = (interval != null && interval.length() > 0) ? interval : DEFAULT_INTERVAL;
    }

    public DateAggregate(String field, String interval, String format) {
        super(field);
        this.interval = (interval != null && interval.length() > 0) ? interval : DEFAULT_INTERVAL;
        this.format = format;
    }

    public String getInterval() {
        return interval;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
