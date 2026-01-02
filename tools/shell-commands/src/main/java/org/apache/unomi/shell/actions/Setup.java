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
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.shell.services.UnomiManagementService;

@Command(scope = "unomi", name = "setup", description = "This will setup some Apache Unomi runtime options")
@Service
public class Setup implements Action {

    @Reference
    UnomiManagementService unomiManagementService;

    @Option(name = "-d", aliases = "--distribution", description = "Unomi Distribution feature to configure", required = true, multiValued = false)
    String distribution = "unomi-distribution-elasticsearch";

    @Option(name = "-f", aliases = "--force", description = "Force setting up distribution feature name even if already exists (use with caution)", required = false, multiValued = false)
    boolean force = false;

    public Object execute() throws Exception {
        System.out.println("Setting up Apache Unomi distribution: " + distribution);
        unomiManagementService.setupUnomiDistribution(distribution, force);
        return null;
    }

}
