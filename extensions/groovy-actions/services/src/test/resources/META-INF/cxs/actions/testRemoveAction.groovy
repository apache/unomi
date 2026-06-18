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
 * Test Groovy action for the testRemoveGroovyAction unit test
 */
@Action(
    id = "testRemoveAction", 
    name = "Test Remove Action", 
    description = "Action for testing remove functionality",
    actionExecutor = "groovy:testRemoveAction",
    parameters = []
)
def execute() {
    logger.info("Executing remove test action")
    return EventService.NO_CHANGE
} 