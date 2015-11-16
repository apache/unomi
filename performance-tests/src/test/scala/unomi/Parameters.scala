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

import scala.concurrent.duration._

/**
 * Parameters for simulation
 */
object Parameters {

  //val baseUrls = List("http://localhost:8181", "http://localhost:9191")
  val baseUrls = List("http://localhost:8181")

  val format = new java.text.SimpleDateFormat("yyyy-MM")
  val minTime: Long = format.parse("2014-01").getTime()
  val maxTime: Long = format.parse("2015-12").getTime()

  val totalTime = 120 minutes
  val numberOfConcurrentUsers = 1000
  val numberOfConcurrentAdminUsers = 250
  val rampUpTime = 3 minutes
  val numberOfSessionsPerUser = 5
  val sessionSizeAverage = 20
  val sessionSizeStdDev = 4

  val loginPercentage: Double = 10.0
  val formEventPercentage: Double = 5.0
  val searchEventPercentage: Double = 5.0

  val delayAverage: Double = 4.5
  val delayStdDev: Double = 0.5

  val navigationDelayAverage: Double = 1.0
  val navigationDelayStdDev: Double = 0.2


}
