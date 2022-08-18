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
package org.apache.unomi.shell.migration;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.service.MigrationConfig;
import org.apache.unomi.shell.migration.service.MigrationContext;
import org.osgi.framework.BundleContext;

import java.io.IOException;

/**
 * @author dgaillard
 * @deprecated use groovy script for implementing new migrations
 */
public interface Migration {
    /**
     * This method is called to execute the migration
     * @param migrationContext      the current migration context (config, history, messaging, logging)
     * @param bundleContext         the bundle context object
     * @throws IOException if there was an error while executing the migration
     * @deprecated do groovy script for implementing new migrations
     */
    void execute(MigrationContext migrationContext, BundleContext bundleContext) throws IOException;
}