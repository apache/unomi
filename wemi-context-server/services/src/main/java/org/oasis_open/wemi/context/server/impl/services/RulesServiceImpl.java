package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.services.RulesService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

@Singleton
@OsgiServiceProvider
public class RulesServiceImpl implements RulesService, EventListenerService  {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    @Inject
    private DefinitionsService definitionsService;

    Map<String, Rule> rules = new LinkedHashMap<String, Rule>();

    @PostConstruct
    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/rules", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedSegmentURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject ruleObject = (JsonObject) jsonst;

                String ruleID = ruleObject.getString("id");
                JsonObject queryObject = ruleObject.getJsonObject("definition");
                StringWriter queryStringWriter = new StringWriter();
                JsonWriter jsonWriter = Json.createWriter(queryStringWriter);
                jsonWriter.writeObject(queryObject);
                jsonWriter.close();
                Rule rule = new Rule();

                Condition condition = ParserHelper.parseCondition(definitionsService, ruleObject.getJsonObject("condition"));
                rule.setRootCondition(condition);

                JsonArray array = ruleObject.getJsonArray("consequences");
                Set<Consequence> consequences = new HashSet<Consequence>();
                for (JsonValue value : array) {
                    try {
                        Consequence consequence = ParserHelper.parseConsequence(definitionsService, (JsonObject) value);
                        consequences.add(consequence);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    }
                }
                rule.setConsequences(consequences);

                rules.put(ruleID, rule);

                persistenceService.saveQuery(ruleID, queryStringWriter.toString());
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }
    }

    public Set<Rule> getMatchingRules(Event event) {
        Set<Rule> matchedRules = new LinkedHashSet<Rule>();

        List<String> matchingQueries = persistenceService.getMatchingSavedQueries(event);

        if (matchingQueries.size() > 0) {
            for (String matchingQuery : matchingQueries) {
                if (rules.containsKey(matchingQuery)) {
                    matchedRules.add(rules.get(matchingQuery));
                }
            }
        }

        return matchedRules;
     }


    public boolean canHandle(Event event) {
        return true;
    }

    public boolean onEvent(Event event) {
        boolean changed = false;
        Set<Rule> rules = getMatchingRules(event);
        for (Rule rule: rules) {
            for (Consequence consequence : rule.getConsequences()) {
                changed |= consequence.apply(event.getUser());
            }
        }
        return changed;
    }
}
