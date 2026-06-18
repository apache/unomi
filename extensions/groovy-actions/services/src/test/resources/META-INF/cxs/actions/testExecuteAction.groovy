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

import org.apache.unomi.api.services.EventService
import org.apache.unomi.groovy.actions.annotations.Action
import org.apache.unomi.groovy.actions.annotations.Parameter

/**
 * Test Groovy action that accepts parameters and returns EventService constants
 */
@Action(
    id = "testExecuteAction", 
    name = "Test Execute Action", 
    description = "Action for testing execution with parameters",
    actionExecutor = "groovy:testExecuteAction",
    parameters = [
        @Parameter(id = "returnType", type = "string", multivalued = false),
        @Parameter(id = "shouldFail", type = "boolean", multivalued = false)
    ]
)
def execute() {
    // Access parameters from the action object
    def returnType = action.getParameterValues().get("returnType")
    def shouldFail = action.getParameterValues().get("shouldFail")
    
    // Log received parameters
    logger.info("Executing action with parameters: returnType=${returnType}, shouldFail=${shouldFail}")
    
    // Check the should fail parameter
    if (shouldFail) {
        logger.error("Action execution failed as requested by shouldFail parameter")
        return EventService.ERROR
    }
    
    // Return based on the returnType parameter
    switch (returnType) {
        case "SESSION_UPDATED":
            return EventService.SESSION_UPDATED
        case "NO_CHANGE":
            return EventService.NO_CHANGE
        default:
            logger.warn("Unknown returnType: ${returnType}, defaulting to NO_CHANGE")
            return EventService.NO_CHANGE
    }
} 