package org.jahia.modules.wemi;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.UserProperties;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Choice list initializer that will retrieve the list of available segments from the WEMI context server.
 */
public class WemSegmentChoiceListInitializer implements ModuleChoiceListInitializer {

    private transient static Logger logger = org.slf4j.LoggerFactory.getLogger(WemSegmentChoiceListInitializer.class);

    public void setKey(String key) {

    }

    public String getKey() {
        return null;
    }

    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {

        JCRNodeWrapper node = (JCRNodeWrapper) context.get("contextNode");
        if (node == null) {
            node = (JCRNodeWrapper) context.get("contextParent");
        }
        if (node != null) {
            try {

                String wemiContextServerURL = node.getResolveSite().hasProperty("wemiContextServerURL") ? node.getResolveSite().getProperty("wemiContextServerURL").getString() : null;

                Client client = ClientBuilder.newBuilder().newClient();
                WebTarget target = client.target(wemiContextServerURL + "cxf/");
                target = target.path("service").queryParam("a", "avalue");

                Invocation.Builder builder = target.request();
                Response response = builder.get();
                Set<SegmentID> segments = (Set<SegmentID>) builder.get(Set.class);

                JahiaUser jahiaUser = node.getSession().getUser();
                UserProperties userProperties = jahiaUser.getUserProperties();
                Set<String> userPropertyNames = new TreeSet<String>();
                List<ChoiceListValue> listValues = new ArrayList<ChoiceListValue>();
                for (String userPropertyName : userPropertyNames) {
                    listValues.add(new ChoiceListValue(userPropertyName, null,
                            node.getSession().getValueFactory().createValue(userPropertyName)));
                }
                return listValues;
            } catch (RepositoryException e) {
                logger.error("Error while building list of user property names", e);
            }
        }
        return new ArrayList<ChoiceListValue>();
    }
}
