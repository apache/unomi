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

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.geonames.services.GeonameEntry;
import org.apache.unomi.geonames.services.GeonamesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/geonames")
@Component(service=GeonamesEndPoint.class,property = "osgi.jaxrs.resource=true")
public class GeonamesEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(GeonamesEndPoint.class.getName());

    @Reference
    private GeonamesService geonamesService;

    public GeonamesEndPoint() {
        logger.info("Initializing geonames service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setGeonamesService(GeonamesService geonamesService) {
        this.geonamesService = geonamesService;
    }

    @GET
    @Path("/reverseGeoCode/{latlon}")
    public List<GeonameEntry> reverseGeoCode(@PathParam("latlon") String latlon, @HeaderParam("Accept-Language") String language) {
        String[] s = latlon.split(",");
        List<GeonameEntry> entries = geonamesService.reverseGeoCode(s[0], s[1]);
        translate(entries, new Locale(language));
        return entries;
    }

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

    @GET
    @Path("/hierarchy/{id}")
    public List<GeonameEntry> getHierarchy(@PathParam("id") String id, @HeaderParam("Accept-Language") String language) {
        List<GeonameEntry> list = geonamesService.getHierarchy(id);
        translate(list, new Locale(language));
        return list;
    }

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
                if (!StringUtils.isEmpty(name) && !name.equals(entry.getCountryCode())) {
                    entry.setName(name);
                }
            }
        }
    }
}
