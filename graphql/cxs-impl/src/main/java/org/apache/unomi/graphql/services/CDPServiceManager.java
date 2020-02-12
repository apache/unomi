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
package org.apache.unomi.graphql.services;

import org.apache.unomi.api.services.ProfileService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = CDPServiceManager.class)
public class CDPServiceManager {

    private ProfileService profileService;

    @Reference
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public ProfileService getProfileService() {
        return profileService;
    }

    public static CDPServiceManager getInstance() {
        BundleContext bundleContext = FrameworkUtil.getBundle(CDPServiceManager.class).getBundleContext();
        ServiceReference<CDPServiceManager> serviceReference = bundleContext.getServiceReference(CDPServiceManager.class);

        return bundleContext.getService(serviceReference);
    }

}
