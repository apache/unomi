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
package org.apache.unomi.shell.dev.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IPAddressCompleter implements Completer {

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();

        // Add common IP addresses and ranges
        Set<String> ipAddresses = new HashSet<>();
        ipAddresses.add("127.0.0.1");
        ipAddresses.add("localhost");
        ipAddresses.add("0.0.0.0");
        ipAddresses.add("::1");
        ipAddresses.add("192.168.0.0/16");
        ipAddresses.add("10.0.0.0/8");
        ipAddresses.add("172.16.0.0/12");
        ipAddresses.add("fc00::/7");

        // Add IP addresses to completer
        for (String ip : ipAddresses) {
            delegate.getStrings().add(ip);
        }

        return delegate.complete(session, commandLine, candidates);
    }
}
