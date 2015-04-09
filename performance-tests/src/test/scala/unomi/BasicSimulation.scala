package unomi

import java.text.SimpleDateFormat
import java.util.UUID
import java.util.concurrent.TimeUnit

import io.gatling.core.Predef.Session
import io.gatling.core.action.builder.RepeatLoopType
import io.gatling.core.feeder
import io.gatling.core.feeder.Record
import io.gatling.core.session._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.core.validation.Validation

import scala.concurrent.duration._

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._

class BasicSimulation extends Simulation {

  // Configuration

  val r = scala.util.Random

  val format = new java.text.SimpleDateFormat("yyyy-MM")
  val minTime: Long = format.parse("2014-01").getTime()
  val maxTime: Long = format.parse("2015-12").getTime()

  val totalTime = 30 minutes
  val numberOfConcurrentUsers = 1000
  val numberOfConcurrentAdminUsers = 0
  val rampUpTime = 120 seconds
  val numberOfSessionsPerUser = 3
  val sessionSize = 10

  val loginPercentage: Double = 10.0
  val formEventPercentage: Double = 5.0
  val searchEventPercentage: Double = 5.0

  val delayAverage: Double = 4.5
  val delayStdDev: Double = 0.5

  val httpProtocol = http
    .baseURLs("http://local1:8181", "http://local2:8181")
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

  val adminHeaders = Map(
    "Origin" -> "http://localhost:8080",
    "Pragma" -> "no-cache",
    "Accept-Encoding" -> "gzip, deflate, sdch",
    "Accept-Language" -> "en",
    "Accept" -> "application/json, text/plain, */*'",
    "Content-Type" -> "application/json;charset=UTF-8"
  )

  // Feeds

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
      "faceBookId" -> (if (r.nextInt(10) > 7) "facebook" + Integer.toString(r.nextInt(10000)) else ""),
      "twitterId" -> (if (r.nextInt(10) > 7) "twitter" + Integer.toString(r.nextInt(10000)) else ""),
      "email" -> (if (r.nextInt(10) > 7) "user" + Integer.toString(r.nextInt(100000)) + "@test.com" else ""),
      "phoneNumber" -> (if (r.nextInt(10) > 7) "001-202-555-" + Integer.toString(1000 + r.nextInt(10000)) else ""),
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
  val linklist = separatedValues("linklist.txt", ' ').random
  val urllistFeed = csv("urllist.txt").random
  val userAgentFeed = csv("userAgent.txt").random
  val wordsFeed = csv("words.txt").random

  val flagNewUser = exec(session => {
    session.set("flag", "New user")
  })

  val flagNewSession = exec(session => {
    if (session.attributes.get("flag").get == "") session.set("flag", "New session") else session
  })

  val unflag = exec(session => {
    session.set("flag", "")
  })

  val updatePreviousURL = exec(session => {
    session.set("previousURL", session.attributes.get("destinationURL").get)
  })

  val pauseAndUpdateTimestamp = pause("${pauseTime}", TimeUnit.MILLISECONDS)
    .exec(session => {
    session.set("timestamp", session.attributes.get("timestamp").get.asInstanceOf[Long] + session.attributes.get("pauseTime").get.asInstanceOf[Int])
  })

  // Browsing requests and scenario

  val loadContext = feed(requestsFeed).feed(urllistFeed).exec(http("LoadContext ${requestTemplate} ${flag}").post("/context.js?sessionId=${sessionId}&timestamp=${timestamp}")
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

  val userScenario = scenario("User").during(totalTime) {
      feed(usersFeed)
      .exec(flagNewUser)
      .repeat("${numberOfSessions}") {
        feed(sessionsFeed).feed(userAgentFeed).feed(ipListFeed)
        .exec(flagNewSession)
        .exec(loadContext)
        .exec(unflag)
        .randomSwitch(loginPercentage -> userLogin)
        .repeat("${sessionSize}") {
          loadContext
          .randomSwitch(
            formEventPercentage -> formEvent,
            searchEventPercentage -> searchEvent
          )
        }
        .exec(flushSessionCookies)
      }
      .exec(flushCookieJar)
  }

  // Admin scenario

  val siteDashboard = feed(requestsFeed).exec(http("Goals list").get("/cxs/goals/ACMESPACE/sitegoals")
    .basicAuth("karaf", "karaf")
    .headers(adminHeaders)
    .check(jsonPath("$..id").findAll.exists.saveAs("goalIds")))

    .exec(http("Site timeline").post("/cxs/query/session/timeStamp")
    .basicAuth("karaf", "karaf")
    .headers(adminHeaders)
    .body(ELFileBody("SiteTimeline_request.json")))

    .foreach("${goalIds}", "goalId") {
      exec(http("Goal widget").post("/cxs/goals/ACMESPACE/${goalId}/report")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("GoalReport_request.json")))
    }
    .pause("${pauseTime}", TimeUnit.MILLISECONDS)

  val adminScenario = scenario("Admin").during(totalTime) {
    siteDashboard
  }


  setUp(
    userScenario.inject(rampUsers(numberOfConcurrentUsers) over rampUpTime)
//    ,
//    adminScenario.inject(rampUsers(numberOfConcurrentAdminUsers) over rampUpTime)
  ).protocols(httpProtocol)

}
