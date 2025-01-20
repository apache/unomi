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
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScoringCompleter;

/**
 * Deletes a scoring
 */
@Command(scope = "unomi", name = "scoring-delete", description = "Deletes a scoring")
@Service
public class ScoringDeleteCommand extends BaseCommand {

    @Argument(index = 0, name = "scoringId", description = "The identifier of the scoring", required = true)
    @Completion(ScoringCompleter.class)
    String scoringId;

    @Option(name = "--force", description = "Skip confirmation", required = false)
    boolean force = false;

    @Reference
    Session session;

    @Override
    public Object execute() throws Exception {
        Scoring scoring = persistenceService.load(scoringId, Scoring.class);
        if (scoring == null) {
            System.out.println("Scoring with id '" + scoringId + "' not found.");
            return null;
        }

        if (!force && !confirm(session, "Are you sure you want to delete scoring '" + scoringId + "'? (yes/no) ")) {
            System.out.println("Deletion cancelled");
            return null;
        }

        persistenceService.remove(scoringId, Scoring.class);
        System.out.println("Scoring '" + scoringId + "' deleted successfully.");
        return null;
    }
} 