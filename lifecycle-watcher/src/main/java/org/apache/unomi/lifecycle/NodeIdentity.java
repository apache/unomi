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
package org.apache.unomi.lifecycle;

import org.apache.commons.lang3.StringUtils;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Identity of this Unomi node: an id that stays the same across restarts and a user friendly name.
 * <p>
 * The id is the configured one when there is one, otherwise the id this node used before, otherwise
 * a new one built from the node name and a random suffix (for example {@code web-1-3f9a2c1b}).
 * The name is the configured one, otherwise the host name. Unlike the id, the name is resolved
 * again at every start, so it follows the configuration or the host name.
 */
final class NodeIdentity {

    /** Configuration (stored as {@code etc/org.apache.unomi.node.cfg}) that remembers the identity. */
    static final String PID = "org.apache.unomi.node";
    static final String NODE_ID = "nodeId";
    static final String NODE_NAME = "nodeName";

    static final String DEFAULT_NODE_NAME = "unomi";
    private static final int MAX_NAME_LENGTH_IN_ID = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    final String nodeId;
    final String nodeName;
    /** Where the id comes from: {@code configured}, {@code stored} or {@code generated}. */
    final String source;

    private NodeIdentity(String nodeId, String nodeName, String source) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.source = source;
    }

    /**
     * @param configuredId   id set by the operator, blank when none
     * @param storedId       id this node used at its previous start, blank when none
     * @param configuredName name set by the operator, blank when none
     * @param hostName       host name of this machine, blank when unknown
     * @param suffix         source of the random suffix of a generated id
     * @return the identity to use for this start
     */
    static NodeIdentity resolve(String configuredId, String storedId, String configuredName, String hostName,
            Supplier<String> suffix) {
        String nodeName = StringUtils.firstNonBlank(configuredName, hostName, DEFAULT_NODE_NAME).trim();
        if (StringUtils.isNotBlank(configuredId)) {
            return new NodeIdentity(configuredId.trim(), nodeName, "configured");
        }
        if (StringUtils.isNotBlank(storedId)) {
            return new NodeIdentity(storedId.trim(), nodeName, "stored");
        }
        return new NodeIdentity(toIdPrefix(nodeName) + "-" + suffix.get(), nodeName, "generated");
    }

    /**
     * Reduces a name to what is safe everywhere a node id ends up (document ids, logs, URLs).
     */
    static String toIdPrefix(String nodeName) {
        String prefix = nodeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        prefix = StringUtils.strip(StringUtils.left(prefix, MAX_NAME_LENGTH_IN_ID), "-");
        return prefix.isEmpty() ? DEFAULT_NODE_NAME : prefix;
    }

    static String randomSuffix() {
        return String.format("%08x", RANDOM.nextInt());
    }

    /**
     * The environment is asked first, since it is what a container sets.
     */
    static String localHostName() {
        String hostName = StringUtils.firstNonBlank(System.getenv("HOSTNAME"), System.getenv("COMPUTERNAME"));
        if (hostName != null) {
            return hostName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }
}
