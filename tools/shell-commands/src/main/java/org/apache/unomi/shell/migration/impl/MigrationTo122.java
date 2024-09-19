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
package org.apache.unomi.shell.migration.impl;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.service.MigrationConfig;
import org.apache.unomi.shell.migration.service.MigrationContext;
import org.apache.unomi.shell.migration.utils.HttpRequestException;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.BundleContext;

import java.io.IOException;

public class MigrationTo122 implements Migration {
    private CloseableHttpClient httpClient;
    private String esAddress;

    @Override
    public void execute(MigrationContext migrationContext, BundleContext bundleContext) throws IOException {
        this.httpClient = migrationContext.getHttpClient();
        this.esAddress = migrationContext.getConfigString(MigrationConfig.CONFIG_ES_ADDRESS);
        deleteOldIndexTemplate(migrationContext);
    }

    private void deleteOldIndexTemplate(MigrationContext migrationContext) throws IOException {
        String oldMonthlyIndexTemplate = "context_monthlyindex";
        try {
            migrationContext.printMessage("Deleting old monthly index template " + oldMonthlyIndexTemplate);
            HttpUtils.executeDeleteRequest(httpClient, esAddress + "/_template/" + oldMonthlyIndexTemplate, null);
        } catch (HttpRequestException e) {
            if (e.getCode() == 404) {
                migrationContext.printMessage("Old monthly index template not found, skipping deletion");
            } else {
                throw e;
            }
        }
    }
}
