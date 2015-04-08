package unomi

import java.text.SimpleDateFormat
import java.util.UUID
import java.util.concurrent.TimeUnit

import io.gatling.core.feeder
import io.gatling.core.feeder.Record
import io.gatling.core.session.SessionAttribute
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.core.validation.Validation

import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class BasicSimulation extends Simulation {

  val httpProtocol = http
    .baseURL("http://local1:8181")
    .inferHtmlResources(WhiteList("""http://local1:8181/.*"""), BlackList())
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .connection("keep-alive")
    .contentTypeHeader("text/plain;charset=UTF-8")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:24.0) Gecko/20100101 Firefox/24.0")

  val headers = Map(
    "Origin" -> "http://localhost:8080",
    "Pragma" -> "no-cache",
    "User-Agent" -> "${userAgent}"
  )

  val uri1 = "http://local1:8181/context.js"

  val r = scala.util.Random

  val format = new java.text.SimpleDateFormat("yyyy-MM")
  val minTime: Long = format.parse("2014-01").getTime()
  val maxTime: Long = format.parse("2015-12").getTime()

  val totalTime = 1 minutes
  val numberOfConcurrentUsers = 500
  val rampUpTime = 20 seconds
  val numberOfSessionsPerUser = 2
  val sessionSize = 10

  val loginPercentage: Double = 50.0
  val formEventPercentage: Double = 10.0
  val searchEventPercentage: Double = 10.0

  val delayAverage: Double = 4.5
  val delayStdDev: Double = 0.5


  val usersFeed = Iterator.continually {
    Map(
      "numberOfSessions" -> numberOfSessionsPerUser
    )
  }

  val sessionsFeed = Iterator.continually {
    Map(
      "sessionId" -> UUID.randomUUID().toString,
      "sessionSize" -> sessionSize,
      "timestamp" -> (minTime + r.nextInt(((maxTime - minTime) / 1000).toInt).toLong * 1000),
      "previousURL" -> "",
      "gender" -> (if (r.nextBoolean()) "male" else "female"),
      "age" -> (15 + r.nextInt(60)),
      "income" -> (10000 * r.nextInt(2000)),
      "faceBookId" -> (if (r.nextInt(10) > 7) "facebook" + Integer.toString(15 + r.nextInt(60)) else ""),
      "twitterId" -> (if (r.nextInt(10) > 7) "twitter" + Integer.toString(15 + r.nextInt(60))  else ""),
      "email" -> (if (r.nextInt(10) > 7) "user" + Integer.toString(15 + r.nextInt(60)) + "@test.com" else ""),
      "phoneNumber" -> (if (r.nextInt(10) > 7) "001-202-555-" + Integer.toString(1000 + r.nextInt(8999)) else ""),
      "leadAssignedTo" -> (if (r.nextInt(10) > 7) "account_manager" + Integer.toString(r.nextInt(10)) + "@test.com" else "")
    )
  }

  val requestsFeed = Iterator.continually {
    Map(
      "pauseTime" -> Math.round((delayAverage + delayStdDev * r.nextGaussian()) * 1000).asInstanceOf[Int],
      "requestTemplate" -> r.nextInt(2)
    )
  }

  val ipListFeed = csv("ipList.txt").random
  val linklist = separatedValues("linklist.txt",' ').random
  val urllistFeed = csv("urllist.txt").random
  val userAgentFeed = csv("userAgent.txt").random
  val wordsFeed = csv("words.txt").random

  val flagNewUser = exec(session => { session.set("flag", "New user") })
  val flagNewSession = exec(session => {
    if (session.attributes.get("flag").get == "") session.set("flag", "New session") else session }
  )
  val unflag = exec(session => { session.set("flag", "") })

  val updatePreviousURL = exec(session => { session.set("previousURL", session.attributes.get("destinationURL").get) })

  val pauseAndUpdateTimestamp = pause("${pauseTime}", TimeUnit.MILLISECONDS)
    .exec(session => { session.set("timestamp", session.attributes.get("timestamp").get.asInstanceOf[Long] + session.attributes.get("pauseTime").get.asInstanceOf[Int]) })
  
  val loadContext = feed(requestsFeed).feed(urllistFeed).exec(http("LoadContext ${flag}").post("/context.js?sessionId=${sessionId}&timestamp=${timestamp}")
    .headers(headers)
    .body(ELFileBody("ContextLoad_request_${requestTemplate}.json")))
    .exec(updatePreviousURL)
    .exec(pauseAndUpdateTimestamp)

  val userLogin = feed(requestsFeed).exec(http("UserLogin").post("/eventcollector?sessionId=${sessionId}&timestamp=${timestamp}")
    .headers(headers)
    .body(ELFileBody("UserLogin_request.json")))
    .exec(pauseAndUpdateTimestamp)

  val formEvent = feed(requestsFeed).exec(http("Form").post("/eventcollector?sessionId=${sessionId}&timestamp=${timestamp}")
    .headers(headers)
    .body(ELFileBody("Form_request.json")))
    .exec(pauseAndUpdateTimestamp)

  val searchEvent = feed(requestsFeed).feed(wordsFeed).exec(http("Search").post("/eventcollector?sessionId=${sessionId}&timestamp=${timestamp}")
    .headers(headers)
    .body(ELFileBody("Search_request.json")))
    .exec(pauseAndUpdateTimestamp)

  val fullUserSession = feed(sessionsFeed).feed(userAgentFeed).feed(ipListFeed)
    .exec(flagNewSession)
    .exec(loadContext)
    .exec(unflag)
    .randomSwitch(loginPercentage -> userLogin)
    .repeat("${sessionSize}")  {
      loadContext
      .randomSwitch(
//          formEventPercentage -> formEvent,
          searchEventPercentage -> searchEvent)
    }
    .exec(flushSessionCookies)

  val user = feed(usersFeed)
    .exec(flagNewUser)
    .repeat("${numberOfSessions}") {
      fullUserSession
    }
    .exec(flushCookieJar)

  val userScenario = scenario("User").during(totalTime) { user }

  setUp(userScenario.inject(rampUsers(numberOfConcurrentUsers) over rampUpTime)).protocols(httpProtocol)

}
