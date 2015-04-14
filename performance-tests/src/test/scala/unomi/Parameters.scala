package unomi

import scala.concurrent.duration._

/**
 * Parameters for simulation
 */
object Parameters {

  val format = new java.text.SimpleDateFormat("yyyy-MM")
  val minTime: Long = format.parse("2014-01").getTime()
  val maxTime: Long = format.parse("2015-12").getTime()

  val totalTime = 5 minutes
  val numberOfConcurrentUsers = 0
  val numberOfConcurrentAdminUsers = 1
  val rampUpTime = 1 minute
  val numberOfSessionsPerUser = 5
  val sessionSizeAverage = 20
  val sessionSizeStdDev = 4

  val loginPercentage: Double = 10.0
  val formEventPercentage: Double = 5.0
  val searchEventPercentage: Double = 5.0

  val delayAverage: Double = 4.5
  val delayStdDev: Double = 0.5


}
