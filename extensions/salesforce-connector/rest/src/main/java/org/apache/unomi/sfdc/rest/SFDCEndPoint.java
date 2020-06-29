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

package org.apache.unomi.sfdc.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.sfdc.services.SFDCService;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/sfdc")
@Component(service=SFDCEndPoint.class,property = "osgi.jaxrs.resource=true")
public class SFDCEndPoint {

    @Reference
    private SFDCService sfdcService;
    private BundleContext bundleContext;

    public SFDCEndPoint() {
        System.out.println("Initializing SFDC service endpoint...");
    }

    @Activate
    public void activate(ComponentContext componentContext) {
        this.bundleContext = componentContext.getBundleContext();
    }

    @WebMethod(exclude = true)
    public void setSFDCService(SFDCService sfdcService) {
        this.sfdcService = sfdcService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @GET
    @Path("/version")
    public Map<String,String> getVersion() {
        Map<String,String> versionInfo = new HashMap<>();
        Dictionary<String,String> bundleHeaders = bundleContext.getBundle().getHeaders();
        Enumeration<String> bundleHeaderKeyEnum = bundleHeaders.keys();
        while (bundleHeaderKeyEnum.hasMoreElements()) {
            String bundleHeaderKey = bundleHeaderKeyEnum.nextElement();
            versionInfo.put(bundleHeaderKey, bundleHeaders.get((bundleHeaderKey)));
        }
        return versionInfo;
    }

    @GET
    @Path("/limits")
    public Map<String,Object> getLimits() {
        return sfdcService.getLimits();
    }

    public void setSfdcService(SFDCService sfdcService) {
        this.sfdcService = sfdcService;
    }
}
