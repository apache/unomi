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
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.ScoringElement;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.ScoringCompleter;

/**
 * Shows details about a specific scoring
 */
@Command(scope = "unomi", name = "scoring-show", description = "Shows details about a specific scoring")
@Service
public class ScoringShowCommand extends BaseCommand {

    @Argument(index = 0, name = "scoringId", description = "The identifier of the scoring", required = true)
    @Completion(ScoringCompleter.class)
    String scoringId;

    @Override
    public Object execute() throws Exception {
        Scoring scoring = persistenceService.load(scoringId, Scoring.class);
        if (scoring == null) {
            System.out.println("Scoring with id '" + scoringId + "' not found.");
            return null;
        }

        ShellTable table = new ShellTable();
        table.column("Property");
        table.column("Value");

        table.addRow().addContent("ID", scoring.getItemId());
        table.addRow().addContent("Name", scoring.getMetadata().getName());
        table.addRow().addContent("Description", scoring.getMetadata().getDescription());
        table.addRow().addContent("Enabled", scoring.getMetadata().isEnabled() ? "Yes" : "No");
        table.addRow().addContent("Scope", scoring.getScope());
        
        if (scoring.getElements() != null && !scoring.getElements().isEmpty()) {
            table.addRow().addContent("Scoring Elements", "");
            for (ScoringElement element : scoring.getElements()) {
                table.addRow().addContent("  Condition", element.getCondition() != null ? element.getCondition().toString() : "");
                table.addRow().addContent("  Value", element.getValue());
            }
        } else {
            table.addRow().addContent("Scoring Elements", "None");
        }

        table.print(System.out);
        return null;
    }
} 