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
package org.apache.unomi.rest.server;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.feature.LoggingFeature;
import org.osgi.service.component.annotations.Component;

/**
 * Rest server bus
 */
@Component(service = Bus.class)
public class RestServerBus extends ExtensionManagerBus implements Bus {
    public RestServerBus() {
        this.getFeatures().add(new LoggingFeature());
        this.getFeatures().add(new org.apache.cxf.metrics.MetricsFeature());
    }
}
