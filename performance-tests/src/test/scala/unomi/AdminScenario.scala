package unomi

import java.util.concurrent.TimeUnit
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.collection.mutable.ListBuffer

/**
 * Administration scenario
 */
object AdminScenario {
  val r = scala.util.Random

  val requestsFeed = Iterator.continually {
    Map(
      "pauseTime" -> Math.round((Parameters.delayAverage + Parameters.delayStdDev * r.nextGaussian()) * 1000).asInstanceOf[Int],
      "navigationPauseTime" -> Math.round((Parameters.navigationDelayAverage + Parameters.navigationDelayStdDev * r.nextGaussian()) * 1000).asInstanceOf[Int],
      "campaignViewCount" -> 3)
  }

  val adminHeaders = Map(
    "Origin" -> "http://localhost:8080",
    "Pragma" -> "no-cache",
    "Accept-Encoding" -> "gzip, deflate, sdch",
    "Accept-Language" -> "en",
    "Accept" -> "application/json, text/plain, */*'",
    "Content-Type" -> "application/json;charset=UTF-8",
    "Authorization" -> "Basic a2FyYWY6a2FyYWY=")

  // load campaign list
  val campaignList = feed(requestsFeed)
    .exec(
      http("Campaign list").post("/cxs/campaigns/query/detailed")
        .headers(adminHeaders)
        .body(ELFileBody("admin/campaigns/list.json"))
        .check(jsonPath("$..itemId").findAll.transform(s => util.Random.shuffle(s)).saveAs("campaignIds"))
        .check(jsonPath("$..totalSize").find.greaterThan("0")))

  // view dashboard for the picked campaign ID
  val campaignDashboard = feed(requestsFeed)
    .exec(http("Campaign details").get("/cxs/campaigns/${campaignId}/detailed")
      .headers(adminHeaders)
      .check(jsonPath("$..numberOfGoals").find.exists.saveAs("numberOfGoals"))
      .check(jsonPath("$..startDate").find.exists.saveAs("startDate"))
      .check(jsonPath("$..endDate").find.exists.saveAs("endDate"))
      .check(jsonPath("$..primaryGoal").find.exists.saveAs("primaryGoalId")))

    .exec(http("Goals").post("/cxs/goals/query")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/goals.json"))
      .check(jsonPath("$..id").findAll.saveAs("goalIds")))

    .exec(http("Average number of visits").post("/cxs/query/session/nbOfVisits/sum/avg")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/average.json"))
      .check(jsonPath("$.._sum").find.exists))

    .exec(http("Timeline").post("/cxs/query/session/timeStamp")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/timeline.json"))
      .check(jsonPath("$.._all").find.exists))

    .exec(http("Events").post("/cxs/campaigns/events/query")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/events.json"))
      .check(jsonPath("$..totalSize").find.exists))

    .foreach("${goalIds}", "goalId") {
      exec(http("Goal report").post("/cxs/goals/${goalId}/report")
        .headers(adminHeaders)
        .body(ELFileBody("admin/campaigns/goal-report.json"))
        .check(jsonPath("$..globalStats").find.exists))
    }

    .exec(http("Timeline primary goal").post("/cxs/query/session/timeStamp")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/timeline-primary-goal.json"))
      .check(jsonPath("$.._all").find.exists))
      
    .exec(http("Events 2").post("/cxs/campaigns/events/query")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/events2.json"))
      .check(jsonPath("$..totalSize").find.exists));

  // view the engaged users for the picked campaign ID
  val campaignEngaged = feed(requestsFeed)
    .exec(http("Profile tags").get("/cxs/definitions/tags/profileTags")
      .headers(adminHeaders)
      .check(jsonPath("$..id").find.is("profileTags")))

    .exec(http("Existing profile properties").get("/cxs/profiles/existingProperties?tagId=profileProperties&itemType=profile")
      .headers(adminHeaders)
      .check(jsonPath("$..itemId").find.exists))
      
    .exec(http("Profile conditions").get("/cxs/definitions/conditions/tags/profileCondition")
      .headers(adminHeaders)
      .check(jsonPath("$..id").find.is("booleanCondition")))
    
    .exec(http("Profile conditions").get("/cxs/definitions/conditions/tags/usableInPastEventCondition")
      .headers(adminHeaders)
      .check(jsonPath("$..id").find.exists))
    
    .exec(http("Profile search").post("/cxs/profiles/search")
      .headers(adminHeaders)
      .body(ELFileBody("admin/campaigns/engaged-users.json"))
      .check(jsonPath("$..list").find.exists)
      .check(jsonPath("$..totalSize").find.greaterThan("0")));

  val campaigns = feed(requestsFeed)
    // load campaign list
    .exec(campaignList)
    .foreach("${campaignIds}", "campaignId", "campaignCounter") {
      // randomly pick 3 campaigns and view the engaged users
      doIf(session => session("campaignCounter").as[Int] < session("campaignViewCount").as[Int]) {
        pause("${pauseTime}", TimeUnit.MILLISECONDS)

          // view dashboard for the picked campaign ID
          .exec(campaignDashboard)
          .pause("${pauseTime}", TimeUnit.MILLISECONDS)

          // go back to campaign list page
          .exec(campaignList)
          .pause("${navigationPauseTime}", TimeUnit.MILLISECONDS)

          // view the engaged users for the picked campaign ID
          .exec(campaignEngaged)
          .pause("${pauseTime}", TimeUnit.MILLISECONDS)

          // go back to campaign list page
          .exec(campaignList)
      }
    }

  // load segment list
  val segmentList = feed(requestsFeed)
    .exec(
      http("Segment list").post("/cxs/segments/query")
        .headers(adminHeaders)
        .body(ELFileBody("admin/segments/segment-query.json"))
        .check(jsonPath("$..id").findAll.saveAs("segmentIds"))
        .check(jsonPath("$..totalSize").find.greaterThan("0")))

  // check count for the segment
  val segmentProfileCount = feed(requestsFeed)
    .exec(
      http("Segment profile count").post("/cxs/query/profile/count")
        .headers(adminHeaders)
        .body(ELFileBody("admin/segments/segment-profile-count.json"))
        .check(bodyString.exists))

  // segments
  val segments = feed(requestsFeed)
    .exec(segmentList)
    .foreach("${segmentIds}", "segmentId") {
      exec(segmentProfileCount)
    }

  // load segment list
  val siteGoalList = feed(requestsFeed)
    .exec(
      http("Site goal list").post("/cxs/goals/query")
        .headers(adminHeaders)
        .body(ELFileBody("admin/site-dashboard/goals.json"))
        .check(jsonPath("$..id").findAll.saveAs("goalIds")))

  val siteGoalReport = feed(requestsFeed)
    .exec(
      http("Site goal report").post("/cxs/goals/${goalId}/report")
        .headers(adminHeaders)
        .body(ELFileBody("admin/site-dashboard/goal-report.json"))
        .check(jsonPath("$..globalStats").find.exists))

  // site dashboard
  val siteDashboard = feed(requestsFeed)
    .exec(siteGoalList)
    .exec(http("Timeline all visits").post("/cxs/query/session/timeStamp")
      .headers(adminHeaders)
      .body(ELFileBody("admin/site-dashboard/timeline-all-visits.json"))
      .check(jsonPath("$.._all").find.exists))
    .foreach("${goalIds}", "goalId") {
      exec(siteGoalReport)
    }
    .exec(http("Timeline new visits").post("/cxs/query/session/timeStamp")
      .headers(adminHeaders)
      .body(ELFileBody("admin/site-dashboard/timeline-new-visits.json"))
      .check(jsonPath("$.._all").find.exists))

  val scnRun = exec(campaigns,
    pause("${pauseTime}", TimeUnit.MILLISECONDS),
    segments,
    pause("${pauseTime}", TimeUnit.MILLISECONDS),
    siteDashboard)

  val scnSingle = scenario("Admin").exec(scnRun)

  val scn = scenario("Admin").during(Parameters.totalTime) {
    scnRun
  }
}
