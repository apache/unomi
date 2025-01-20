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
package org.apache.unomi.shell.commands.scoring;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.shell.commands.BaseCommand;

import java.util.ArrayList;

/**
 * Creates a new scoring
 */
@Command(scope = "unomi", name = "scoring-create", description = "Creates a new scoring")
@Service
public class ScoringCreateCommand extends BaseCommand {

    @Argument(index = 0, name = "scoringId", description = "The identifier of the scoring", required = true)
    String scoringId;

    @Argument(index = 1, name = "name", description = "The name of the scoring", required = true)
    String name;

    @Option(name = "--description", description = "The description of the scoring", required = false)
    String description;

    @Option(name = "--scope", description = "The scope of the scoring", required = false)
    String scope = "systemscope";

    @Option(name = "--disabled", description = "Create the scoring in disabled state", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Scoring scoring = new Scoring();
        scoring.setItemId(scoringId);
        
        Metadata metadata = new Metadata();
        metadata.setId(scoringId);
        metadata.setName(name);
        metadata.setDescription(description);
        metadata.setEnabled(!disabled);
        metadata.setScope(scope);
        scoring.setMetadata(metadata);
        
        scoring.setElements(new ArrayList<>());
        scoring.setScope(scope);

        persistenceService.save(scoring);
        System.out.println("Scoring '" + scoringId + "' created successfully.");
        return null;
    }
} 