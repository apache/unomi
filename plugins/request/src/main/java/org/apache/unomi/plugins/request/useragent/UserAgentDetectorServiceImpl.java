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

import nl.basjes.parse.useragent.PackagedRules;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import nl.basjes.parse.useragent.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fpapon@apache.org
 */
public class UserAgentDetectorServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAgentDetectorServiceImpl.class.getName());

    private UserAgentAnalyzer userAgentAnalyzer;

    public void postConstruct() {
        final UserAgentAnalyzer.UserAgentAnalyzerBuilder userAgentAnalyzerBuilder = UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .immediateInitialization();

        // We bypass the default resources lookup that is using some Spring class lookup in pattern:
        // "classpath*:UserAgents/**/*.yaml" and it's not working on Felix OSGI env
        userAgentAnalyzerBuilder.dropDefaultResources();
        for (String ruleFileName : PackagedRules.getRuleFileNames()) {
            // We don't want test rules
            if (!ConfigLoader.isTestRulesOnlyFile(ruleFileName)) {
                userAgentAnalyzerBuilder.addResources("classpath*:" + ruleFileName);
            }
        }

        // Use custom cache for jdk8 compatibility
        if (getCurrentJVMMajorVersion() < 11) {
            LOGGER.info("Use JVM 8 compliant version of the agent analyzer caching");
            userAgentAnalyzerBuilder.useJava8CompatibleCaching();
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
        LOGGER.info("UserAgentDetector service initialized.");
    }

    private int getCurrentJVMMajorVersion() {
        String[] versionElements = System.getProperty("java.version").split("\\.");
        int discard = Integer.parseInt(versionElements[0]);
        // Versions prior to 10 are named 1.x
        if (discard == 1) {
            return Integer.parseInt(versionElements[1]);
        } else {
            return discard;
        }
    }

    public void preDestroy() {
        if (userAgentAnalyzer != null) {
            userAgentAnalyzer.destroy();
            userAgentAnalyzer = null;
        }
        LOGGER.info("UserAgentDetector service shutdown.");
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

        LOGGER.debug(userAgent.toString());

        return userAgent;
    }
}
