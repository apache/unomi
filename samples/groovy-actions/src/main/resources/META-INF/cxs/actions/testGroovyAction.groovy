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
import org.jsoup.nodes.Document

import static groovyx.net.http.HttpBuilder.configure

Document page = configure {
    request.uri = 'https://mvnrepository.com/artifact/org.apache.groovy/groovy-all'
}.get()

String license = page.select('span.b.lic').collect { it.text() }.join(', ')

println "Event type:${event.getEventType()}"
println "Profile ID=${event.getProfile().getItemId()}"
println "Action name=${action.actionType.metadata.name}"
println "Action parameters=${action.parameterValues}"

println "Groovy is licensed under: ${license}"

EventService.NO_CHANGE
