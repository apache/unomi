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
package org.apache.unomi.services.impl.scheduler;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class SchedulerServiceImplTest {

    @Test
    public void getTimeDiffInSeconds_whenGiveHourOfDay_shouldReturnDifferenceInSeconds(){
        //Arrange
        SchedulerServiceImpl service = new SchedulerServiceImpl();
        int hourToRunInUtc = 11;
        ZonedDateTime timeNowInUtc = ZonedDateTime.of(LocalDateTime.parse("2020-01-13T10:00:00"), ZoneOffset.UTC);
        //Act
        long seconds = service.getTimeDiffInSeconds(hourToRunInUtc, timeNowInUtc);
        //Assert
        assertEquals(3600, seconds);
    }
}
