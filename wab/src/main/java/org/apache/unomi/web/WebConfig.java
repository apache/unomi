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
package org.apache.unomi.web;

import org.apache.unomi.api.services.ConfigSharingService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jerome Blanchard
 */
@Component(service = WebConfig.class, immediate = true, configurationPid = "org.apache.unomi.web")
@Designate(ocd = WebConfig.Config.class)
public class WebConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebConfig.class.getName());

    private String contextserverDomain;
    private String contexserverProfileIdCookieName;
    private int contextserverProfileIdCookieMaxAgeInSeconds;
    private boolean contextserverProfileIdCookieHttpOnly;
    private String[] allowedProfileDownloadFormats;
    private int publicPostRequestBytesLimit;

    @Reference
    private ConfigSharingService configSharingService;

    @ObjectClassDefinition
    public @interface Config {

        @AttributeDefinition
        String contextserver_domain() default "";

        @AttributeDefinition
        String contexserver_profileIdCookieName() default "context-profile-id";

        @AttributeDefinition
        int contextserver_profileIdCookieMaxAgeInSeconds() default 31536000;

        @AttributeDefinition
        boolean contextserver_profileIdCookieHttpOnly() default false;

        @AttributeDefinition
        String[] allowed_profile_download_formats() default {"csv", "yaml", "json", "text"};

        @AttributeDefinition
        int public_post_request_bytes_limit() default 200000;

    }

    public WebConfig() {
        LOGGER.info("WebConfig created.");
    }

    @Activate
    public void activate(Config config) {
        this.contextserverDomain = config.contextserver_domain();
        this.contexserverProfileIdCookieName = config.contexserver_profileIdCookieName();
        this.contextserverProfileIdCookieMaxAgeInSeconds = config.contextserver_profileIdCookieMaxAgeInSeconds();
        this.contextserverProfileIdCookieHttpOnly = config.contextserver_profileIdCookieHttpOnly();
        this.allowedProfileDownloadFormats = config.allowed_profile_download_formats();
        this.publicPostRequestBytesLimit = config.public_post_request_bytes_limit();

        configSharingService.setProperty("profileIdCookieName", contexserverProfileIdCookieName);
        configSharingService.setProperty("profileIdCookieDomain", contextserverDomain);
        configSharingService.setProperty("profileIdCookieMaxAgeInSeconds", contextserverProfileIdCookieMaxAgeInSeconds);
        configSharingService.setProperty("profileIdCookieHttpOnly", contextserverProfileIdCookieHttpOnly);
        configSharingService.setProperty("publicPostRequestBytesLimit", publicPostRequestBytesLimit);
        configSharingService.setProperty("allowedProfileDownloadFormats", allowedProfileDownloadFormats);
        LOGGER.info("WebConfig activated.");
    }

    public String getContextserverDomain() {
        return contextserverDomain;
    }

    public String getContexserverProfileIdCookieName() {
        return contexserverProfileIdCookieName;
    }

    public int getContextserverProfileIdCookieMaxAgeInSeconds() {
        return contextserverProfileIdCookieMaxAgeInSeconds;
    }

    public boolean isContextserverProfileIdCookieHttpOnly() {
        return contextserverProfileIdCookieHttpOnly;
    }

    public String[] getAllowedProfileDownloadFormats() {
        return allowedProfileDownloadFormats;
    }

    public int getPublicPostRequestBytesLimit() {
        return publicPostRequestBytesLimit;
    }
}
