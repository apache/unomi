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
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScoringCompleter;

/**
 * Updates an existing scoring
 */
@Command(scope = "unomi", name = "scoring-update", description = "Updates an existing scoring")
@Service
public class ScoringUpdateCommand extends BaseCommand {

    @Argument(index = 0, name = "scoringId", description = "The identifier of the scoring", required = true)
    @Completion(ScoringCompleter.class)
    String scoringId;

    @Option(name = "--name", description = "The name of the scoring", required = false)
    String name;

    @Option(name = "--description", description = "The description of the scoring", required = false)
    String description;

    @Option(name = "--scope", description = "The scope of the scoring", required = false)
    String scope;

    @Option(name = "--enabled", description = "Enable the scoring", required = false)
    boolean enabled = false;

    @Option(name = "--disabled", description = "Disable the scoring", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Scoring scoring = persistenceService.load(scoringId, Scoring.class);
        if (scoring == null) {
            System.out.println("Scoring with id '" + scoringId + "' not found.");
            return null;
        }

        if (enabled && disabled) {
            System.out.println("Cannot specify both --enabled and --disabled options.");
            return null;
        }

        boolean modified = false;

        if (name != null) {
            scoring.getMetadata().setName(name);
            modified = true;
        }

        if (description != null) {
            scoring.getMetadata().setDescription(description);
            modified = true;
        }

        if (scope != null) {
            scoring.getMetadata().setScope(scope);
            scoring.setScope(scope);
            modified = true;
        }

        if (enabled || disabled) {
            scoring.getMetadata().setEnabled(enabled);
            modified = true;
        }

        if (!modified) {
            System.out.println("No updates specified.");
            return null;
        }

        persistenceService.save(scoring);
        System.out.println("Scoring '" + scoringId + "' updated successfully.");
        return null;
    }
} 