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
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpRequestException;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Component;

import java.io.IOException;
import java.util.Map;

public class MigrationTo122 implements Migration {
    private CloseableHttpClient httpClient;
    private Session session;
    private String esAddress;

    @Override
    public void execute(Session session, CloseableHttpClient httpClient, Map<String, Object> migrationConfig, BundleContext bundleContext) throws IOException {
        this.httpClient = httpClient;
        this.session = session;
        this.esAddress = (String) migrationConfig.get("esAddress");
        deleteOldIndexTemplate();

    }

    private void deleteOldIndexTemplate() throws IOException {
        String oldMonthlyIndexTemplate = "context_monthlyindex";
        try {
            ConsoleUtils.printMessage(session,"Deleting old monthly index template " + oldMonthlyIndexTemplate);
            HttpUtils.executeDeleteRequest(httpClient, esAddress + "/_template/" + oldMonthlyIndexTemplate, null);
        } catch (HttpRequestException e) {
            if (e.getCode() == 404) {
                ConsoleUtils.printMessage(session,"Old monthly index template not found, skipping deletion");
            } else {
                throw e;
            }
        }

    }
}
