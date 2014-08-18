package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

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
public class SetRemoteHostInfoConsequence implements ConsequenceExecutor {
    @Override
    public boolean execute(Consequence consequence, Event event) {
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
            session.setProperty("country_code", location.getString("country_code"));
            session.setProperty("country_name", location.getString("country_name"));
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

        return true;
    }
}
