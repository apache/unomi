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

package org.apache.unomi.geonames.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.geonames.services.GeonameEntry;
import org.apache.unomi.geonames.services.GeonamesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JAX-RS endpoint for GeoNames lookups (reverse geocoding, hierarchies, cities, capitals).
 */
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/geonames")
@Component(service=GeonamesEndPoint.class,property = "osgi.jaxrs.resource=true")
public class GeonamesEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeonamesEndPoint.class.getName());

    @Reference
    private GeonamesService geonamesService;

    public GeonamesEndPoint() {
        LOGGER.info("Initializing geonames service endpoint...");
    }

    public void setGeonamesService(GeonamesService geonamesService) {
        this.geonamesService = geonamesService;
    }

    /**
     * Reverse-geocodes a latitude/longitude pair.
     * <p>
     * {@code latlon} must be {@code lat,lon} (comma-separated). Country names may be localized via {@code Accept-Language}.
     *
     * @param latlon comma-separated latitude and longitude
     * @param language optional {@code Accept-Language} header for localized country names
     * @return matching geoname entries
     * @api.status 200 array org.apache.unomi.geonames.services.GeonameEntry Matching entries (may be empty).
     * @api.example [{"itemId":"2988507","itemType":"geonameEntry","name":"Paris","countryCode":"FR","featureCode":"PPLC"}]
     */
    @GET
    @Path("/reverseGeoCode/{latlon}")
    public List<GeonameEntry> reverseGeoCode(@PathParam("latlon") String latlon, @HeaderParam("Accept-Language") String language) {
        String[] s = latlon.split(",");
        List<GeonameEntry> entries = geonamesService.reverseGeoCode(s[0], s[1]);
        translate(entries, new Locale(language));
        return entries;
    }

    /**
     * Returns child geoname entries along a slash-separated hierarchy path.
     *
     * @param items path segments describing the parent hierarchy (e.g. continent/country/region)
     * @param language optional {@code Accept-Language} header for localized country names
     * @return child entries (up to 999)
     * @api.status 200 org.apache.unomi.api.PartialList GeonameEntry page (list items are GeonameEntry; may be empty).
     * @api.example {"list":[{"itemId":"3017382","itemType":"geonameEntry","name":"France","countryCode":"FR","featureCode":"PCLI"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/entries/{items:.*}")
    public PartialList<GeonameEntry> getChildrenEntries(@PathParam("items") List<PathSegment> items, @HeaderParam("Accept-Language") String language) {
        List<String> l = new ArrayList<>();
        for (PathSegment item : items) {
            l.add(item.getPath());
        }
        PartialList<GeonameEntry> list = geonamesService.getChildrenEntries(l, 0, 999);
        translate(list.getList(), new Locale(language));
        return list;
    }

    /**
     * Returns child city entries along a slash-separated hierarchy path.
     *
     * @param items path segments describing the parent hierarchy
     * @param language optional {@code Accept-Language} header for localized country names
     * @return child cities (up to 999)
     * @api.status 200 org.apache.unomi.api.PartialList GeonameEntry page (list items are GeonameEntry; may be empty).
     * @api.example {"list":[{"itemId":"2988507","itemType":"geonameEntry","name":"Paris","countryCode":"FR","featureCode":"PPLC"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/cities/{items:.*}")
    public PartialList<GeonameEntry> getChildrenCities(@PathParam("items") List<PathSegment> items, @HeaderParam("Accept-Language") String language) {
        List<String> l = new ArrayList<>();
        for (PathSegment item : items) {
            l.add(item.getPath());
        }
        PartialList<GeonameEntry> list = geonamesService.getChildrenCities(l, 0, 999);
        translate(list.getList(), new Locale(language));
        return list;
    }

    /**
     * Returns the administrative hierarchy for the given geoname id.
     *
     * @param id the geoname identifier
     * @param language optional {@code Accept-Language} header for localized country names
     * @return hierarchy entries from root to the requested id
     * @api.status 200 array org.apache.unomi.geonames.services.GeonameEntry Hierarchy entries (may be empty).
     * @api.example [{"itemId":"6255148","itemType":"geonameEntry","name":"Europe","featureCode":"CONT"},{"itemId":"3017382","itemType":"geonameEntry","name":"France","countryCode":"FR","featureCode":"PCLI"}]
     */
    @GET
    @Path("/hierarchy/{id}")
    public List<GeonameEntry> getHierarchy(@PathParam("id") String id, @HeaderParam("Accept-Language") String language) {
        List<GeonameEntry> list = geonamesService.getHierarchy(id);
        translate(list, new Locale(language));
        return list;
    }

    /**
     * Returns capital entries for the given parent geoname id.
     *
     * @param id the parent geoname identifier
     * @param language optional {@code Accept-Language} header for localized country names
     * @return capital entries
     * @api.status 200 array org.apache.unomi.geonames.services.GeonameEntry Capital entries (may be empty).
     * @api.example [{"itemId":"2988507","itemType":"geonameEntry","name":"Paris","countryCode":"FR","featureCode":"PPLC"}]
     */
    @GET
    @Path("/capitals/{id}")
    public List<GeonameEntry> getCapitalEntries(@PathParam("id") String id, @HeaderParam("Accept-Language") String language) {
        List<GeonameEntry> list = geonamesService.getCapitalEntries(id);
        translate(list, new Locale(language));
        return list;
    }

    private void translate(List<GeonameEntry> l, Locale locale) {
        for (GeonameEntry entry : l) {
            if (GeonamesService.COUNTRY_FEATURE_CODES.contains(entry.getFeatureCode())) {
                String name = new Locale("", entry.getCountryCode()).getDisplayCountry(locale);
                if (StringUtils.isNotEmpty(name) && !name.equals(entry.getCountryCode())) {
                    entry.setName(name);
                }
            }
        }
    }
}
