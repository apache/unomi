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
package org.apache.unomi.tracing.impl;

import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.service.component.annotations.Component;

/**
 * Default implementation of the TracerService
 */
@Component(service = TracerService.class, immediate = true)
public class DefaultTracerService implements TracerService {

    private final ThreadLocal<RequestTracer> currentTracer = new ThreadLocal<RequestTracer>() {
        @Override
        protected RequestTracer initialValue() {
            return new DefaultRequestTracer();
        }
    };

    @Override
    public RequestTracer getCurrentTracer() {
        return currentTracer.get();
    }

    @Override
    public void enableTracing() {
        RequestTracer tracer = getCurrentTracer();
        tracer.setEnabled(true);
        tracer.reset();
    }

    @Override
    public void disableTracing() {
        RequestTracer tracer = getCurrentTracer();
        tracer.setEnabled(false);
        tracer.reset();
    }

    @Override
    public boolean isTracingEnabled() {
        return getCurrentTracer().isEnabled();
    }

    @Override
    public String getTraceAsJson() {
        return getCurrentTracer().getTraceAsJson();
    }

    public void cleanup() {
        currentTracer.remove();
    }
} 