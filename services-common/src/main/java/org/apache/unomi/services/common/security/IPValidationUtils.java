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

package org.apache.unomi.services.common.security;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Utility class for IP address validation and authorization.
 * Provides shared functionality for checking if a source IP address is authorized
 * against a set of allowed IP addresses or CIDR ranges.
 */
public class IPValidationUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(IPValidationUtils.class);
    
    /**
     * Check if a source IP address is authorized against a set of allowed IP addresses.
     * 
     * @param sourceIP the source IP address to validate
     * @param authorizedIPs the set of authorized IP addresses or CIDR ranges
     * @return true if the source IP is authorized, false otherwise
     */
    public static boolean isIpAuthorized(String sourceIP, Set<String> authorizedIPs) {
        if (authorizedIPs == null || authorizedIPs.isEmpty()) {
            return true; // No IP restrictions
        }
        
        if (StringUtils.isBlank(sourceIP)) {
            return false;
        }
        
        try {
            // Handle IPv6 addresses with brackets
            if (sourceIP.startsWith("[") && sourceIP.endsWith("]")) {
                // This can happen with IPv6 addresses, we must remove the markers since our IPAddress library doesn't support them.
                sourceIP = sourceIP.substring(1, sourceIP.length() - 1);
                // If the result is empty or only whitespace, it's invalid
                if (StringUtils.isBlank(sourceIP)) {
                    return false;
                }
            }
            
            IPAddress eventIP = new IPAddressString(sourceIP).toAddress();

            for (String authorizedIP : authorizedIPs) {
                try {
                    IPAddress ip = new IPAddressString(authorizedIP.trim()).toAddress();
                    if (ip.contains(eventIP)) {
                        return true;
                    }
                } catch (Exception e) {
                    // Log invalid IP in configuration but continue checking others
                    LOGGER.warn("Invalid IP address in configuration: {}. Skipping.", authorizedIP);
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Invalid source IP address: {}", sourceIP, e);
            return false;
        }
    }
} 