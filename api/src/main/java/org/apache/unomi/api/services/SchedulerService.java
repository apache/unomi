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

package org.apache.unomi.api.services;

import java.util.concurrent.ScheduledExecutorService;

/**
 * A service to access {@link ScheduledExecutorService} to execute {@link java.util.TimerTask}
 * Use this service instead of creating and using a new {@link java.util.Timer}
 *
 * https://stackoverflow.com/questions/409932/java-timer-vs-executorservice
 */
public interface SchedulerService {

    /**
     * Use this method to get a {@link ScheduledExecutorService}
     * and execute your task with it instead of using {@link java.util.Timer}
     *
     * @return {@link ScheduledExecutorService}
     */
    ScheduledExecutorService getScheduleExecutorService();
}
