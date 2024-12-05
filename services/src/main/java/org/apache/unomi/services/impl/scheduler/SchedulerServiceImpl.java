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

import org.apache.unomi.api.services.SchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author dgaillard
 */
public class SchedulerServiceImpl implements SchedulerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class.getName());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService sharedScheduler;
    private int threadPoolSize;

    public void postConstruct() {
        sharedScheduler = Executors.newScheduledThreadPool(threadPoolSize);
        LOGGER.info("Scheduler service initialized.");
    }

    public void preDestroy() {
        sharedScheduler.shutdown();
        scheduler.shutdown();
        LOGGER.info("Scheduler service shutdown.");
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    @Override
    public ScheduledExecutorService getScheduleExecutorService() {
        return scheduler;
    }


    @Override
    public ScheduledExecutorService getSharedScheduleExecutorService() {
        return sharedScheduler;
    }

    public static long getTimeDiffInSeconds(int hourInUtc, ZonedDateTime now) {
        ZonedDateTime nextRun = now.withHour(hourInUtc).withMinute(0).withSecond(0);
        if(now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).getSeconds();
    }
}
