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

import org.apache.felix.service.command.CommandSession;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.shell.migration.Migration;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.apache.unomi.shell.migration.utils.HttpRequestException;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.osgi.framework.Version;

import java.io.IOException;

public class MigrationTo122 implements Migration {
    private CloseableHttpClient httpClient;
    private CommandSession session;
    private String esAddress;

    @Override
    public Version getFromVersion() {
        return null;
    }

    @Override
    public Version getToVersion() {
        return new Version("1.2.2");
    }

    @Override
    public void execute(CommandSession session, CloseableHttpClient httpClient, String esAddress) throws IOException {
        this.httpClient = httpClient;
        this.session = session;
        this.esAddress = esAddress;
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
