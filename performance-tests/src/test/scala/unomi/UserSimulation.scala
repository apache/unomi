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
 * limitations under the License
 */

package unomi

import io.gatling.core.Predef._
import io.gatling.core.structure.{PopulatedScenarioBuilder, ScenarioBuilder}
import io.gatling.http.Predef._

class UserSimulation extends Simulation {


  val httpProtocol = http
    .baseURLs(Parameters.baseUrls)
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .connection("keep-alive")
    .contentTypeHeader("text/plain;charset=UTF-8")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:24.0) Gecko/20100101 Firefox/24.0")


//     setUp(UserScenario.scnSingle.inject(atOnceUsers(1)).protocols(httpProtocol))
    
  val m = Map(
    UserScenario.scn -> Parameters.numberOfConcurrentUsers
  )
  val f = m.collect { case (scenario: ScenarioBuilder, count: Int) if count > 0 => scenario.inject(rampUsers(count) over Parameters.rampUpTime) }
  setUp(f.asInstanceOf[List[PopulatedScenarioBuilder]]).protocols(httpProtocol)

}
