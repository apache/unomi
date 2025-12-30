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
package org.apache.unomi.shell.actions;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.shell.services.UnomiManagementService;

@Command(scope = "unomi", name = "start", description = "This will start Apache Unomi")
@Service
public class Start implements Action {

    @Reference
    UnomiManagementService unomiManagementService;

    @Argument(name = "startFeatures", description = "Start features configuration to use (elasticsearch/opensearch)", valueToShowInHelp = "elasticsearch")
    private String selectedStartFeatures = "elasticsearch";

    @Option(name = "-i", aliases = "--install-only", description = "Only install features, don't start them", required = false, multiValued = false)
    boolean installOnly = false;

    public Object execute() throws Exception {
        if (!selectedStartFeatures.equals("elasticsearch") &&
                !selectedStartFeatures.equals("opensearch")) {
            System.err.println("Invalid value '"+selectedStartFeatures+"' specified for start features configuration, will default to elasticsearch");
            selectedStartFeatures = "elasticsearch";
        }
        System.out.println("Starting Apache Unomi with start features configuration: " + selectedStartFeatures);
        unomiManagementService.startUnomi(selectedStartFeatures, !installOnly);
        return null;
    }

}
