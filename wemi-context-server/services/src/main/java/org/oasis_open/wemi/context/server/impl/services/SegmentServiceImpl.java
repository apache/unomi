package org.oasis_open.wemi.context.server.impl.services;

import org.mvel2.MVEL;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.ContainerInitialized;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import javax.print.DocFlavor;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
@Singleton
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService {

    Map<SegmentID, Serializable> segmentExpressions = new LinkedHashMap<SegmentID, Serializable>();
    Map<SegmentID, Serializable> segmentQueries = new LinkedHashMap<SegmentID, Serializable>();

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    public SegmentServiceImpl() {
        System.out.println("Initializing segment service...");

        // @Todo remove hardcoded segments, make them configurable, maybe storing them as ElasticSearch Queries and then using the Percolate API to match with users ?
        segmentExpressions.put(new SegmentID("alwaysTrue", "All users", "This segment includes all users"), MVEL.compileExpression("true"));
        segmentExpressions.put(new SegmentID("maleGender", "Men", "This segment includes all men"), MVEL.compileExpression("user.properties.?gender == 'male'"));
        segmentExpressions.put(new SegmentID("goal1Reached", "Goal 1 Reached", "This segment includes all users that have reached goal 1"), MVEL.compileExpression("user.properties.?goal1 == 'reached'"));

    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("postConstruct {" + bundleContext.getBundle() + "}");
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/segments", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            System.out.println("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedSegmentURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject segmentObject = (JsonObject) jsonst;
                SegmentID segmentID = new SegmentID(segmentObject.getString("id"), segmentObject.getString("name"), segmentObject.getString("description"));
                String segmentType = segmentObject.getString("type");
                if ("es-query".equals(segmentType)) {
                    JsonObject queryObject = segmentObject.getJsonObject("definition");
                    StringWriter queryStringWriter = new StringWriter();
                    JsonWriter jsonWriter = Json.createWriter(queryStringWriter);
                    jsonWriter.writeObject(queryObject);
                    jsonWriter.close();
                    segmentQueries.put(segmentID, queryStringWriter.toString());
                    persistenceService.saveQuery(segmentID.getId(), queryStringWriter.toString());
                } else if ("mvel".equals(segmentType)) {
                    String segmentMvelExpression = segmentObject.getString("definition");
                    segmentExpressions.put(segmentID, MVEL.compileExpression(segmentMvelExpression));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }
    }

    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
        return null;
    }

    public Boolean isUserInSegment(User user, SegmentID segmentID) {

        Set<SegmentID> matchingSegments = getSegmentsForUser(user);

        return matchingSegments.contains(segmentID);
    }

    public Set<SegmentID> getSegmentsForUser(User user) {

        Set<SegmentID> matchedSegments = new LinkedHashSet<SegmentID>();

        Map vars = new HashMap();
        vars.put("user", user);

        for (Map.Entry<SegmentID, Serializable> segmentExpressionEntry : segmentExpressions.entrySet()) {

            // Now we execute it.
            Boolean result = (Boolean) MVEL.executeExpression(segmentExpressionEntry.getValue(), vars);

            if (result.booleanValue()) {
                matchedSegments.add(segmentExpressionEntry.getKey());
            }

        }

        // @todo implement user segment matching using ElasticSearch's Percolate API and using the query definitions

        return matchedSegments;
    }

    public Set<SegmentID> getSegmentIDs() {
        return segmentExpressions.keySet();
    }

    public Set<SegmentDefinition> getSegmentDefinition(SegmentID segmentID) {
        return null;
    }

    public Set<ConditionTag> getConditionTags() {
        return null;
    }

    public Set<Condition> getConditions(ConditionTag conditionTag) {
        return null;
    }

    public Set<ConditionParameter> getConditionParameters(Condition condition) {
        return null;
    }

    public static void dumpJSON(JsonValue tree, String key, String depthPrefix) {
       if (key != null)
          System.out.print(depthPrefix + "Key " + key + ": ");
       switch(tree.getValueType()) {
          case OBJECT:
             System.out.println(depthPrefix + "OBJECT");
             JsonObject object = (JsonObject) tree;
             for (String name : object.keySet())
                 dumpJSON(object.get(name), name, depthPrefix + "  ");
             break;
          case ARRAY:
             System.out.println(depthPrefix + "ARRAY");
             JsonArray array = (JsonArray) tree;
             for (JsonValue val : array)
                 dumpJSON(val, null, depthPrefix + "  ");
             break;
          case STRING:
             JsonString st = (JsonString) tree;
             System.out.println(depthPrefix + "STRING " + st.getString());
             break;
          case NUMBER:
             JsonNumber num = (JsonNumber) tree;
             System.out.println(depthPrefix + "NUMBER " + num.toString());
             break;
          case TRUE:
          case FALSE:
          case NULL:
             System.out.println(depthPrefix + tree.getValueType().toString());
             break;
       }
    }

}
