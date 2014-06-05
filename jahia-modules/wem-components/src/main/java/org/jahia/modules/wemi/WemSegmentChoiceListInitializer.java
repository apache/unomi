package org.jahia.modules.wemi;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Choice list initializer that will retrieve the list of available segments from the WEMI context server.
 */
public class WemSegmentChoiceListInitializer implements ModuleChoiceListInitializer {

    private transient static Logger logger = org.slf4j.LoggerFactory.getLogger(WemSegmentChoiceListInitializer.class);
    private String key;

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {

        JCRNodeWrapper node = (JCRNodeWrapper) context.get("contextNode");
        if (node == null) {
            node = (JCRNodeWrapper) context.get("contextParent");
        }
        if (node != null) {
            try {

                String wemiContextServerURL = node.getResolveSite().hasProperty("wemiContextServerURL") ? node.getResolveSite().getProperty("wemiContextServerURL").getString() : null;

                Client client = ClientBuilder.newClient();
                WebTarget target = client.target(wemiContextServerURL).path("cxf/wemi");

                Invocation.Builder invocationBuilder =
                        target.request(MediaType.APPLICATION_JSON_TYPE);

                Response response = invocationBuilder.get();
                Set<Map<String,String>> segments = (Set<Map<String,String>>) response.readEntity(Set.class);

                List<ChoiceListValue> listValues = new ArrayList<ChoiceListValue>();
                for (Map<String,String> segmentID : segments) {
                    String displayName = segmentID.get("name");
                    if (displayName == null) {
                        segmentID.get("id");
                    } else {
                        if (segmentID.get("description") != null) {
                            displayName += " (" + segmentID.get("description") + ")";
                        }
                    }
                    listValues.add(new ChoiceListValue(displayName, null,
                            node.getSession().getValueFactory().createValue(segmentID.get("id"))));
                }
                return listValues;
            } catch (RepositoryException e) {
                logger.error("Error while building list of WEM segments", e);
            }
        }
        return new ArrayList<ChoiceListValue>();
    }
}
