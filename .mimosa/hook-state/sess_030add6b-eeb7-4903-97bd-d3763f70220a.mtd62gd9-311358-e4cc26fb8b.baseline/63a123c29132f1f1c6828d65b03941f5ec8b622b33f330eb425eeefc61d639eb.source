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

package org.apache.unomi.api;

/**
 * Common contract for pluggable Unomi definition types loaded from OSGi.
 * Implementations expose a stable id and an OSGi target filter so the runtime
 * can locate executors and validators (for example {@link PropertyMergeStrategyType}
 * and {@link ValueType}).
 */
public interface PluginType {

    /**
     * OSGi bundle id of the plugin that registered this type.
     *
     * @return the plugin bundle id
     */
    long getPluginId();

    /**
     * Associates this plugin type with its OSGi bundle.
     *
     * @param pluginId the plugin bundle id
     */
    void setPluginId(long pluginId);

}
