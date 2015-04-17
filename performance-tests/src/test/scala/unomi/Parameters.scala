package unomi

import scala.concurrent.duration._

/**
 * Parameters for simulation
 */
object Parameters {

  val baseUrls = List("http://localhost:8181")

  val format = new java.text.SimpleDateFormat("yyyy-MM")
  val minTime: Long = format.parse("2014-01").getTime()
  val maxTime: Long = format.parse("2015-12").getTime()

  val totalTime = 5 minutes
  val numberOfConcurrentUsers = 100
  val numberOfConcurrentAdminUsers = 0
  val rampUpTime = 2 minute
  val numberOfSessionsPerUser = 5
  val sessionSizeAverage = 20
  val sessionSizeStdDev = 4

  val loginPercentage: Double = 10.0
  val formEventPercentage: Double = 5.0
  val searchEventPercentage: Double = 5.0

  val delayAverage: Double = 4.5
  val delayStdDev: Double = 0.5


}
