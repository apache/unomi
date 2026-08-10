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
import groovy.transform.Field

// Stand-in for the reported upload-time RCE payload: a @Field initializer runs when the script class
// is *instantiated*. Saving this action must compile it without instantiating it, so this must NOT
// run. It writes a system property rather than executing a command so the test stays harmless.
@Field def sideEffect = { System.setProperty("unomi.test.groovyFieldInitializerRan", "true") }()

@Action(id = "fieldInitializerAction", actionExecutor = "groovy:fieldInitializerAction")
def execute() {
    return EventService.NO_CHANGE
}
