<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

Metrics
=======

This project makes it easy to add metrics to your project, which will then give you the following features:

- Count the number a of time a metric was executed
- Get the accumulated time some measured code took
- See the call stacks to a metric (deactivated by default in order to minimize performance impact)

Adding metrics to your project : 

In order to minimize the impact of metrics in your project, we recommend you use the following pattern to integrate 
metrics:

        long startTime = System.currentTimeMillis();
        // code to be mesured should be here.
        if (metricsService != null && metricsService.isActivated()) {
            metricsService.updateTimer(this.getClass().getName() + YOUR_METRIC_NAME, startTime);
        }
        
This will handle all the proper cases of metrics being deactivated as well as even the service not being available.        