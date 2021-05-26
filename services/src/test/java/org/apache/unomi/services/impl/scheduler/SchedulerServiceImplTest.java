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