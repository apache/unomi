/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.request.actions;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class SetRemoteHostInfoAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SetRemoteHostInfoAction.class.getName());

    private DatabaseReader databaseReader;
    private String pathToGeoLocationDatabase;

    private String defaultSessionCountryCode = "CH";
    private String defaultSessionCountryName = "Switzerland";
    private String defaultSessionCity = "Geneva";
    private String defaultSessionAdminSubDiv1 = "2660645";
    private String defaultSessionAdminSubDiv2 = "6458783";
    private String defaultSessionIsp = "Cablecom";
    private double defaultLatitude = 46.1884341;
    private double defaultLongitude = 6.1282508;

    public void setPathToGeoLocationDatabase(String pathToGeoLocationDatabase) {
        this.pathToGeoLocationDatabase = pathToGeoLocationDatabase;
    }

    public void setDefaultSessionCountryCode(String defaultSessionCountryCode) {
        this.defaultSessionCountryCode = defaultSessionCountryCode;
    }

    public void setDefaultSessionCountryName(String defaultSessionCountryName) {
        this.defaultSessionCountryName = defaultSessionCountryName;
    }

    public void setDefaultSessionCity(String defaultSessionCity) {
        this.defaultSessionCity = defaultSessionCity;
    }

    public void setDefaultSessionAdminSubDiv1(String defaultSessionAdminSubDiv1) {
        this.defaultSessionAdminSubDiv1 = defaultSessionAdminSubDiv1;
    }

    public void setDefaultSessionAdminSubDiv2(String defaultSessionAdminSubDiv2) {
        this.defaultSessionAdminSubDiv2 = defaultSessionAdminSubDiv2;
    }

    public void setDefaultSessionIsp(String defaultSessionIsp) {
        this.defaultSessionIsp = defaultSessionIsp;
    }

    public void setDefaultLatitude(double defaultLatitude) {
        this.defaultLatitude = defaultLatitude;
    }

    public void setDefaultLongitude(double defaultLongitude) {
        this.defaultLongitude = defaultLongitude;
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

        String remoteAddr = httpServletRequest.getRemoteAddr();
        String remoteAddrParameter = httpServletRequest.getParameter("remoteAddr");
        String xff = httpServletRequest.getHeader("X-Forwarded-For");
        if (remoteAddrParameter != null && remoteAddrParameter.length() > 0) {
            remoteAddr = remoteAddrParameter;
        } else if (xff != null && !xff.equals("")) {
            if (xff.indexOf(',') > -1) {
                xff = xff.substring(0, xff.indexOf(','));
            }
            remoteAddr = xff;
        }

        try {
            if (isAValidIPAddress(remoteAddr)) {
                ipLookupInDatabase(remoteAddr, session);
            } else {
                session.setProperty("sessionCountryCode", defaultSessionCountryCode);
                session.setProperty("sessionCountryName", defaultSessionCountryName);
                session.setProperty("sessionCity", defaultSessionCity);
                session.setProperty("sessionAdminSubDiv1", defaultSessionAdminSubDiv1);
                session.setProperty("sessionAdminSubDiv2", defaultSessionAdminSubDiv2);
                session.setProperty("sessionIsp", defaultSessionIsp);
                Map<String, Double> location = new HashMap<String, Double>();
                location.put("lat", defaultLatitude);
                location.put("lon", defaultLongitude);
                session.setProperty("location", location);
            }
            session.setProperty("countryAndCity", session.getProperty("sessionCountryName") + "@@" + session.getProperty("sessionCity") +
                    "@@" + session.getProperty("sessionAdminSubDiv1") + "@@" + session.getProperty("sessionAdminSubDiv2"));
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

    private boolean ipLookupInDatabase(String remoteAddr, Session session) {
        if (databaseReader == null) {
            return false;
        }

        try {
            // Replace "city" with the appropriate method for your database, e.g.,
            // "country".
            CityResponse cityResponse = databaseReader.city(InetAddress.getByName(remoteAddr));

            if (cityResponse.getCountry().getName() != null) {
                session.setProperty("sessionCountryCode", cityResponse.getCountry().getIsoCode());
                session.setProperty("sessionCountryName", cityResponse.getCountry().getName());
            }
            if (cityResponse.getCity().getName() != null) {
                session.setProperty("sessionCity", cityResponse.getCity().getName());
                session.setProperty("sessionCityId", cityResponse.getCity().getGeoNameId());
            }

            if (cityResponse.getSubdivisions().size() > 0) {
                session.setProperty("sessionAdminSubDiv1", cityResponse.getSubdivisions().get(0).getGeoNameId());
            }
            if (cityResponse.getSubdivisions().size() > 1) {
                session.setProperty("sessionAdminSubDiv2", cityResponse.getSubdivisions().get(1).getGeoNameId());
            }
            String isp = databaseReader.isp(InetAddress.getByName(remoteAddr)).getIsp();
            if (isp != null) {
                session.setProperty("sessionIsp", isp);
            }

            Map<String, Double> locationMap = new HashMap<String, Double>();
            if (cityResponse.getLocation().getLatitude() != null && cityResponse.getLocation().getLongitude() != null) {
                locationMap.put("lat", cityResponse.getLocation().getLatitude());
                locationMap.put("lon", cityResponse.getLocation().getLongitude());
                session.setProperty("location", locationMap);
            }
            return true;
        } catch (IOException | GeoIp2Exception e) {
            logger.debug("Cannot resolve IP", e);
        }
        return false;
    }

    private static boolean isAValidIPAddress(String remoteAddr) {
        if (InetAddressUtils.isIPv4Address(remoteAddr) || InetAddressUtils.isIPv6Address(remoteAddr)) {
            InetAddress addr;
            try {
                addr = InetAddress.getByName(remoteAddr);
            } catch (UnknownHostException e) {
                logger.debug("Cannot resolve IP", e);
                return false;
            }
            // Check if the address is a valid special local or loop back
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
                return false;
            }

            // Check if the address is not defined on any interface
            try {
                return NetworkInterface.getByInetAddress(addr) == null;
            } catch (SocketException e) {
                return false;
            }
        }
        return false;
    }
}
