//package org.oasis_open.wemi.context.server.impl.services
//
//import javax.json._
//
//import org.oasis_open.wemi.context.server.api.Event
//import org.oasis_open.wemi.context.server.api.consequences.Consequence
//import org.oasis_open.wemi.context.server.api.services.{DefinitionsService, EventListenerService, RulesService}
//import org.oasis_open.wemi.context.server.impl.consequences.ConsequenceExecutorDispatcher
//import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService
//import org.osgi.framework.BundleContext
//import org.slf4j.{Logger, LoggerFactory}
//
//import scala.collection.JavaConversions._
//import scala.collection.mutable
//
//class RulesServiceScalaImpl extends RulesService with EventListenerService {
//  private final val logger: Logger = LoggerFactory.getLogger(classOf[RulesServiceScalaImpl].getName)
//
//  var _bundleContext: BundleContext = null
//
//  def persistenceService: PersistenceService = null
//
//  def definitionsService: DefinitionsService = null
//
//  def bundleContext_=(context: BundleContext) = this._bundleContext = context
//  def bundleContext = this._bundleContext
//
//  private[services] var rules = Map[String, Rule]()
//
//  def postConstruct() {
//    logger.debug("postConstruct {" + bundleContext.getBundle + "}")
//    val predefinedSegmentEntries: java.util.Enumeration[java.net.URL] = bundleContext.getBundle.findEntries("META-INF/rules", "*.json", true)
//    predefinedSegmentEntries.foreach(predefinedSegmentURL => {
//      logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ")
//      val reader: JsonReader = Json.createReader(predefinedSegmentURL.openStream)
//      try {
//        val ruleObject = reader.read.asInstanceOf[JsonObject]
//        val ruleID = ruleObject.getString("id")
//        val rule: Rule = new Rule
//        rule.setRootCondition(ParserHelper.parseCondition(definitionsService, ruleObject.getJsonObject("condition")))
//        val map: mutable.Buffer[Consequence] = ruleObject.getJsonArray("consequences").map { x => ParserHelper.parseConsequence(definitionsService, x.asInstanceOf[JsonObject])}
//        rule.setConsequences(map.toSet[Consequence])
//        persistenceService.saveQuery(ruleID, rule.getRootCondition)
//        rules += (ruleID -> rule)
//      }
//      catch {
//        case e: Exception => {
//          logger.error("Error while loading segment definition " + predefinedSegmentURL, e)
//        }
//      }
//      finally {
//        if (reader != null) {
//          reader.close
//        }
//      }
//    })
//  }
//
//  def onEvent(event: Event): Boolean = {
//    val consequenceExecutor: ConsequenceExecutorDispatcher = new ConsequenceExecutorDispatcher(event.getUser)
//    getMatchingRules(event).foreach { _.getConsequences.foreach(consequenceExecutor.execute) }
//    consequenceExecutor.isChanged
//  }
//
//  def getMatchingRules(event: Event): Set[Rule] = {
//    val buffer: mutable.Buffer[Rule] = persistenceService.getMatchingSavedQueries(event) filter { rules.containsKey } map { rules.get(_).get }
//    buffer.toSet[Rule]
//  }
//
//  override def canHandle(event: Event): Boolean = true
//}
