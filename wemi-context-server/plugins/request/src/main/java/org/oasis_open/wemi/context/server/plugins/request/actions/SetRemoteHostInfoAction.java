package org.oasis_open.wemi.context.server.plugins.request.actions;

import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Created by toto on 18/08/14.
 */
public class SetRemoteHostInfoAction implements ActionExecutor {
    @Override
    public boolean execute(Action action, Event event) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        Session session = event.getSession();
        if (session == null) {
            return false;
        }

        session.setProperty("remoteAddr", httpServletRequest.getRemoteAddr());
        session.setProperty("remoteHost", httpServletRequest.getRemoteHost());
        InputStream inputStream = null;

        try {
            final URL url = new URL("http://freegeoip.net/json/" + httpServletRequest.getRemoteAddr());
            inputStream = url.openConnection().getInputStream();
            JsonReader reader = Json.createReader(inputStream);
            JsonObject location = (JsonObject) reader.read();
            session.setProperty("countryCode", location.getString("country_code"));
            session.setProperty("countryName", location.getString("country_name"));
            session.setProperty("city",location.getString("city"));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
        ReadableUserAgent agent = parser.parse(httpServletRequest.getHeader("User-Agent"));
        session.setProperty("operatingSystemFamily", agent.getOperatingSystem().getFamilyName());
        session.setProperty("operatingSystemName", agent.getOperatingSystem().getName());
        session.setProperty("userAgentName", agent.getName());
        session.setProperty("userAgentVersion", agent.getVersionNumber().toVersionString());
        session.setProperty("deviceCategory", agent.getDeviceCategory().getName());

        return true;
    }
}
