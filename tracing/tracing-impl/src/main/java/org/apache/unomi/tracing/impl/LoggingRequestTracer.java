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

import org.apache.unomi.api.conditions.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of RequestTracer that extends DefaultRequestTracer to leverage its tree structure
 * while also logging operations to SLF4J
 */
public class LoggingRequestTracer extends DefaultRequestTracer {
    private static final Logger logger = LoggerFactory.getLogger(LoggingRequestTracer.class);

    public LoggingRequestTracer(boolean enabled) {
        setEnabled(enabled);
    }

    @Override
    public void startOperation(String operationType, String description, Object context) {
        super.startOperation(operationType, description, context);
        if (!isEnabled()) return;
        
        if (context instanceof Condition) {
            Condition condition = (Condition) context;
            logger.debug("Starting {} of condition {} - {}", operationType, condition.getConditionTypeId(), description);
        } else {
            logger.debug("Starting {} - {}", operationType, description);
        }
    }

    @Override
    public void endOperation(Object result, String description) {
        super.endOperation(result, description);
        if (!isEnabled()) return;
        logger.debug("Finished - result={} - {}", result, description);
    }

    @Override
    public void trace(String message, Object context) {
        super.trace(message, context);
        if (!isEnabled()) return;
        
        if (context instanceof Condition) {
            Condition condition = (Condition) context;
            logger.debug("Condition {} - {}", condition.getConditionTypeId(), message);
        } else {
            logger.debug("{}", message);
        }
    }
} 