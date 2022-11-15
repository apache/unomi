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

package org.apache.unomi.plugins.request.useragent;

import nl.basjes.parse.useragent.AbstractUserAgentAnalyzer;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.apache.commons.collections.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author fpapon@apache.org
 */
public class UserAgentDetectorServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(UserAgentDetectorServiceImpl.class.getName());

    private final static float JAVA_CLASS_VERSION_JDK11 = 55.0f;
    private final static String JAVA_CLASS_VERSION = "java.class.version";

    private UserAgentAnalyzer userAgentAnalyzer;

    public void postConstruct() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            final UserAgentAnalyzer.UserAgentAnalyzerBuilder userAgentAnalyzerBuilder = UserAgentAnalyzer
                    .newBuilder()
                    .hideMatcherLoadStats()
                    .immediateInitialization();
            if (Float.parseFloat(System.getProperty(JAVA_CLASS_VERSION)) < JAVA_CLASS_VERSION_JDK11) {
                // Use custom cache for jdk8 compatibility
                userAgentAnalyzerBuilder.withCacheInstantiator(
                                (AbstractUserAgentAnalyzer.CacheInstantiator) size ->
                                        Collections.synchronizedMap(new LRUMap(size)))
                        .withClientHintCacheInstantiator(
                                (AbstractUserAgentAnalyzer.ClientHintsCacheInstantiator<?>) size ->
                                        Collections.synchronizedMap(new LRUMap(size)));
            }
            this.userAgentAnalyzer = userAgentAnalyzerBuilder.withCache(10000)
                    .withField(nl.basjes.parse.useragent.UserAgent.OPERATING_SYSTEM_CLASS)
                    .withField(nl.basjes.parse.useragent.UserAgent.OPERATING_SYSTEM_NAME)
                    .withField(nl.basjes.parse.useragent.UserAgent.AGENT_NAME)
                    .withField(nl.basjes.parse.useragent.UserAgent.AGENT_VERSION)
                    .withField(nl.basjes.parse.useragent.UserAgent.DEVICE_CLASS)
                    .withField(nl.basjes.parse.useragent.UserAgent.DEVICE_NAME)
                    .withField(nl.basjes.parse.useragent.UserAgent.DEVICE_BRAND)
                    .build();
            this.userAgentAnalyzer.initializeMatchers();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
        logger.info("UserAgentDetector service initialized.");
    }

    public void preDestroy() {
        userAgentAnalyzer = null;
        logger.info("UserAgentDetector service shutdown.");
    }

    public UserAgent parseUserAgent(String header) {
        nl.basjes.parse.useragent.UserAgent yauaaAgent = userAgentAnalyzer.parse(header);

        UserAgent userAgent = new UserAgent();
        userAgent.setDeviceCategory(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.DEVICE_CLASS));
        userAgent.setDeviceName(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.DEVICE_NAME));
        userAgent.setDeviceBrand(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.DEVICE_BRAND));
        userAgent.setOperatingSystemFamily(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.OPERATING_SYSTEM_CLASS));
        userAgent.setOperatingSystemName(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.OPERATING_SYSTEM_NAME));
        userAgent.setUserAgentName(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.AGENT_NAME));
        userAgent.setUserAgentVersion(yauaaAgent.getValue(nl.basjes.parse.useragent.UserAgent.AGENT_VERSION));

        if (logger.isDebugEnabled()) {
            logger.debug(userAgent.toString());
        }

        return userAgent;
    }
}
