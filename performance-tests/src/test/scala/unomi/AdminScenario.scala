package unomi

import java.util.concurrent.TimeUnit

import io.gatling.core.Predef._
import io.gatling.http.Predef._

/**
 * Administration scenario
 */
object AdminScenario {
  val r = scala.util.Random

  val requestsFeed = Iterator.continually {
    Map(
      "pauseTime" -> Math.round((Parameters.delayAverage + Parameters.delayStdDev * r.nextGaussian()) * 1000).asInstanceOf[Int]
    )
  }

  val adminHeaders = Map(
    "Origin" -> "http://localhost:8080",
    "Pragma" -> "no-cache",
    "Accept-Encoding" -> "gzip, deflate, sdch",
    "Accept-Language" -> "en",
    "Accept" -> "application/json, text/plain, */*'",
    "Content-Type" -> "application/json;charset=UTF-8"
  )

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

  val campaignDashboard = feed(requestsFeed).exec(http("Campaign dashboard").get("/cxs/campaigns/ACMESPACE/${campaignId}")
    .basicAuth("karaf", "karaf")
    .headers(adminHeaders))
    .exec(http("Goals list").get("/cxs/goals/${campaignId}/campaign")
    .basicAuth("karaf", "karaf")
    .headers(adminHeaders)
    .check(jsonPath("$..id").findAll.exists.saveAs("goalIds")))

    .foreach("${goalIds}", "goalId") {
    exec(http("Goal widget").post("/cxs/goals/ACMESPACE/${goalId}/report")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("GoalReport_request.json")))
  }
    .pause("${pauseTime}", TimeUnit.MILLISECONDS)


  val campaignList = feed(requestsFeed).exec(http("Campaign list").get("/cxs/campaigns")
    .basicAuth("karaf", "karaf")
    .headers(adminHeaders)
    .check(jsonPath("$..id").findAll.exists.saveAs("campaignIds")))

    .foreach("${campaignIds}", "campaignId") {
    exec(http("Get campaign info").get("/cxs/campaigns/ACMESPACE/${campaignId}")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders))

      .exec(http("Count engaged profile").post("/cxs/query/profile/count")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("Campaign_profile_count.json")))

      .exec(http("Count engaged sessions").post("/cxs/query/session/count")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("Campaign_session_count1.json")))

      .exec(http("Count session matching primary goal").post("/cxs/query/session/count")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("Campaign_session_count2.json")))

      .exec(http("Count goals in campaign").post("/cxs/query/goal/count")
      .basicAuth("karaf", "karaf")
      .headers(adminHeaders)
      .body(ELFileBody("Campaign_goal_count.json")))
  }
    .pause("${pauseTime}", TimeUnit.MILLISECONDS)
    .foreach("${campaignIds}", "campaignId") {
    campaignDashboard
      .pause("${pauseTime}", TimeUnit.MILLISECONDS)
  }


  val scn = scenario("Admin").during(Parameters.totalTime) {
    exec(siteDashboard, campaignList, campaignDashboard)
  }

}
