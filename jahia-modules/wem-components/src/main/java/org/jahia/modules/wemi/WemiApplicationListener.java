package org.jahia.modules.wemi;

import org.jahia.bin.Logout;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.jahia.services.content.JCRSessionWrapper;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;

/**
 * Created by loom on 07.06.14.
 */
public class WemiApplicationListener implements ApplicationListener {
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        if (applicationEvent instanceof LoginEngineAuthValveImpl.LoginEvent) {
            LoginEngineAuthValveImpl.LoginEvent loginEvent = (LoginEngineAuthValveImpl.LoginEvent) applicationEvent;
            AuthValveContext authValveContext = loginEvent.getAuthValveContext();
            String redirectURL = authValveContext.getRequest().getParameter("redirect");
            int siteIndex = redirectURL.indexOf("/sites/");
            if (siteIndex > -1) {
                int lastSlashIndex = redirectURL.lastIndexOf("/");
                int firstDotIndex = redirectURL.indexOf(".", lastSlashIndex);
                String nodePath = null;
                if (firstDotIndex > -1) {
                    nodePath = redirectURL.substring(siteIndex, firstDotIndex);
                } else {
                    nodePath = redirectURL.substring(siteIndex);
                }
                if (nodePath == null) {
                    return;
                }
                try {
                    JCRSessionWrapper session = authValveContext.getSessionFactory().getCurrentUserSession();
                    Item siteItem = session.getItem(nodePath);
                    if (siteItem == null) {
                        return;
                    }
                    Node siteNode = (Node) siteItem;
                    String wemiContextServerURL = siteNode.hasProperty("wemiContextServerURL") ? siteNode.getProperty("wemiContextServerURL").getString() : null;
                    Client client = ClientBuilder.newClient();
                    WebTarget target = client.target(wemiContextServerURL).path("cxf/wemi");

                    Invocation.Builder invocationBuilder =
                            target.request(MediaType.APPLICATION_JSON_TYPE);

                    Response response = invocationBuilder.get();
                    Set<Map<String,String>> segments = (Set<Map<String,String>>) response.readEntity(Set.class);

                    // @todo complete steps to augment WEMI profile with Jahia user properties

                } catch (RepositoryException e) {
                    e.printStackTrace();
                }
            }
        } else if (applicationEvent instanceof Logout.LogoutEvent) {
            Logout.LogoutEvent logoutEvent = (Logout.LogoutEvent) applicationEvent;
        }
    }
}
