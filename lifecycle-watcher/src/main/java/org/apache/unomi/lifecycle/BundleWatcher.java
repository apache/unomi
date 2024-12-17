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

import org.apache.unomi.api.ServerInfo;

import java.util.List;

/**
 * Interface for the bundle watcher system in Apache Unomi. It allows to know if startup has completed as well as
 * server information such as identifier, versions, build information and more.
 */
public interface BundleWatcher {

    /**
     * Retrieves the list of the server information objects, that include extensions. Each object includes the
     * name and version of the server, build time information and the event types
     * if recognizes as well as the capabilities supported by the system.
     *
     * @return a list of ServerInfo objects with all the server information
     */
    List<ServerInfo> getServerInfos();

    boolean isStartupComplete();

    boolean allAdditionalBundleStarted();

    public void addRequiredBundle(String bundleName);

    public boolean removeRequiredBundle(String bundleName);
}
