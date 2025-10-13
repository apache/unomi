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
package org.apache.unomi.shell.services;

import org.osgi.framework.BundleException;

/**
 * This service provide method to manage unomi
 * @author dgaillard
 */
public interface UnomiManagementService {

    /**
     * This method will start Apache Unomi with the specified start features configuration
     * @param selectedStartFeatures the start features configuration to use
     * @param mustStartFeatures true if features should be started, false if they should not
     * @throws BundleException if there was an error starting Unomi's bundles
     */
    void startUnomi(String selectedStartFeatures, boolean mustStartFeatures) throws Exception;

    /**
     * This method will start Apache Unomi with the specified start features configuration
     * @param selectedStartFeatures the start features configuration to use
     * @param mustStartFeatures true if features should be started, false if they should not
     * @param waitForCompletion true if the method should wait for completion, false if it should not
     * @throws BundleException if there was an error starting Unomi's bundles
     */
    void startUnomi(String selectedStartFeatures, boolean mustStartFeatures, boolean waitForCompletion) throws Exception;

    /**
     * This method will stop Apache Unomi
     * @throws BundleException if there was an error stopping Unomi's bundles
     */
    void stopUnomi() throws Exception;

    /**
     * This method will stop Apache Unomi
     * @param waitForCompletion true if the method should wait for completion, false if it should not
     * @throws BundleException if there was an error stopping Unomi's bundles
     */
    void stopUnomi(boolean waitForCompletion) throws Exception;
}
