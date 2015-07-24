package org.oasis_open.contextserver.plugins.request.actions;

/*
 * #%L
 * Context Server Plugin - Provides request reading actions
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SetRemoteHostInfoAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SetRemoteHostInfoAction.class.getName());

    public static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");

    private DatabaseReader databaseReader;
    private String pathToGeoLocationDatabase;

    public void setPathToGeoLocationDatabase(String pathToGeoLocationDatabase) {
        this.pathToGeoLocationDatabase = pathToGeoLocationDatabase;
    }

    @Override
    public int execute(Action action, Event event) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);
        if (httpServletRequest == null) {
            return EventService.NO_CHANGE;
        }
        Session session = event.getSession();
        if (session == null) {
            return EventService.NO_CHANGE;
        }

        session.setProperty("remoteAddr", httpServletRequest.getRemoteAddr());
        session.setProperty("remoteHost", httpServletRequest.getRemoteHost());

        try {
            if (httpServletRequest.getParameter("remoteAddr") != null && httpServletRequest.getParameter("remoteAddr").length() > 0) {
                ipLookup(httpServletRequest.getParameter("remoteAddr"), session);
            } else if (!httpServletRequest.getRemoteAddr().equals("127.0.0.1") && IPV4.matcher(httpServletRequest.getRemoteAddr()).matches()) {
                ipLookup(httpServletRequest.getRemoteAddr(), session);
            } else {
                session.setProperty("countryCode", "CH");
                session.setProperty("countryName", "Switzerland");
                session.setProperty("city", "Geneva");
                Map<String, Double> location = new HashMap<String, Double>();
                location.put("lat", 46.1884341);
                location.put("lon", 6.1282508);
                session.setProperty("location", location);
            }
            session.setProperty("countryAndCity", session.getProperty("countryName") + "@@" + session.getProperty("city"));
        } catch (Exception e) {
            logger.error("Cannot lookup IP", e);
        }

        UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
        ReadableUserAgent agent = parser.parse(httpServletRequest.getHeader("User-Agent"));
        session.setProperty("operatingSystemFamily", agent.getOperatingSystem().getFamilyName());
        session.setProperty("operatingSystemName", agent.getOperatingSystem().getName());
        session.setProperty("userAgentName", agent.getName());
        session.setProperty("userAgentVersion", agent.getVersionNumber().toVersionString());
        session.setProperty("userAgentNameAndVersion", session.getProperty("userAgentName") + "@@" + session.getProperty("userAgentVersion"));
        session.setProperty("deviceCategory", agent.getDeviceCategory().getName());

        return EventService.SESSION_UPDATED;
    }

    private boolean ipLookup(String remoteAddr, Session session) {
        boolean result = false;
        if (databaseReader != null) {
            result = ipLookupInDatabase(remoteAddr, session);
        } else {
            result = ipLookupInFreeWebService(remoteAddr, session);
        }
        return result;
    }

    private boolean ipLookupInFreeWebService(String remoteAddr, Session session) {
        final URL url;
        InputStream inputStream = null;
        try {
            url = new URL("http://www.telize.com/geoip/" + remoteAddr);
            inputStream = url.openConnection().getInputStream();
            JsonReader reader = Json.createReader(inputStream);
            JsonObject location = (JsonObject) reader.read();
            session.setProperty("countryCode", location.getString("country_code"));
            session.setProperty("countryName", location.getString("country"));
            session.setProperty("city", location.getString("city"));

            Map<String, Double> locationMap = new HashMap<String, Double>();
            locationMap.put("lat", location.getJsonNumber("latitude").doubleValue());
            locationMap.put("lon", location.getJsonNumber("longitude").doubleValue());
            session.setProperty("location", locationMap);
            return true;
        } catch (IOException e) {
            logger.error("Cannot get geoip database",e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error("Cannot close", e);
                }
            }
        }
        return false;
    }

    @PostConstruct
    public void postConstruct() {
        // A File object pointing to your GeoIP2 or GeoLite2 database
        if (pathToGeoLocationDatabase == null) {
            return;
        }
        File database = new File(pathToGeoLocationDatabase);
        if (!database.exists()) {
            return;
        }

        // This creates the DatabaseReader object, which should be reused across
        // lookups.
        try {
            this.databaseReader = new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            logger.error("Cannot read IP database", e);
        }

    }

    public boolean ipLookupInDatabase(String remoteAddr, Session session) {
        if (databaseReader == null) {
            return false;
        }

        // Replace "city" with the appropriate method for your database, e.g.,
        // "country".
        CityResponse cityResponse = null;
        try {
            cityResponse = databaseReader.city(InetAddress.getByName(remoteAddr));
            session.setProperty("countryCode", cityResponse.getCountry().getIsoCode());
            session.setProperty("countryName", cityResponse.getCountry().getName());
            session.setProperty("city", cityResponse.getCity().getName());

            Map<String, Double> locationMap = new HashMap<String, Double>();
            locationMap.put("lat", cityResponse.getLocation().getLatitude());
            locationMap.put("lon", cityResponse.getLocation().getLongitude());
            session.setProperty("location", locationMap);
            return true;
        } catch (IOException | GeoIp2Exception e) {
            logger.debug("Cannot resolve IP", e);
        }
        return false;
    }
}
