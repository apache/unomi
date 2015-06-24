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


  val m = Map(
    UserScenario.scn -> Parameters.numberOfConcurrentUsers
  )
  val f = m.collect { case (scenario: ScenarioBuilder, count: Int) if count > 0 => scenario.inject(rampUsers(count) over Parameters.rampUpTime) }
  setUp(f.asInstanceOf[List[PopulatedScenarioBuilder]]).protocols(httpProtocol)

}
