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
package org.apache.unomi.persistence.spi.aggregate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DateAggregateTest {

    @Test
    public void acceptsShortAndNamedIntervals() {
        for (String interval : new String[]{"1s", "1m", "1h", "1d", "1w", "1M", "1q", "1y",
                "Second", "Minute", "Hour", "Day", "Week", "Month", "Quarter", "Year"}) {
            assertEquals(interval, new DateAggregate("properties.lastVisit", interval).getInterval());
        }
    }

    @Test
    public void defaultsToAMonthWithoutAnInterval() {
        assertEquals("1M", new DateAggregate("properties.lastVisit").getInterval());
        assertEquals("1M", new DateAggregate("properties.lastVisit", null).getInterval());
        assertEquals("1M", new DateAggregate("properties.lastVisit", "", "yyyy-MM-dd").getInterval());
    }

    @Test
    public void rejectsIntervalsTheEnginesCannotRun() {
        for (String interval : new String[]{"2d", "day", "30m", "1D", "Days"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DateAggregate("properties.lastVisit", interval));
            assertTrue(e.getMessage(), e.getMessage().contains("'" + interval + "'"));
            assertTrue(e.getMessage(), e.getMessage().contains("1d"));
        }
    }

    @Test
    public void setIntervalKeepsTheValidIntervalWhenRejecting() {
        DateAggregate aggregate = new DateAggregate("properties.lastVisit", "Day");
        assertThrows(IllegalArgumentException.class, () -> aggregate.setInterval("2d"));
        assertEquals("Day", aggregate.getInterval());
    }
}
