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

    /**
     * Indicates whether Unomi startup has completed. Implementations typically track
     * required bundles and initialization tasks to decide when the system is fully ready.
     *
     * @return {@code true} if startup is complete; {@code false} otherwise
     */
    boolean isStartupComplete();

    /**
     * Indicates whether all additional (optional) bundles configured as required have
     * started successfully.
     *
     * @return {@code true} if all additional required bundles have started; {@code false} otherwise
     */
    boolean allAdditionalBundleStarted();

    /**
     * Registers a bundle symbolic name as required for startup completion. Implementations
     * should monitor its lifecycle and include it in readiness checks.
     *
     * @param bundleName the bundle symbolic name to add as required
     */
    public void addRequiredBundle(String bundleName);

    /**
     * Unregisters a previously required bundle symbolic name from startup checks.
     *
     * @param bundleName the bundle symbolic name to remove
     * @return {@code true} if the bundle was previously registered and got removed; {@code false} otherwise
     */
    public boolean removeRequiredBundle(String bundleName);
}
