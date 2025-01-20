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

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.shell.commands.BaseListCommand;

/**
 * Lists all scorings in Apache Unomi
 */
@Command(scope = "unomi", name = "scoring-list", description = "Lists all scorings in Apache Unomi")
@Service
public class ScoringListCommand extends BaseListCommand<Scoring> {

    @Override
    protected Class<Scoring> getItemType() {
        return Scoring.class;
    }

    @Override
    protected void printItem(ShellTable table, Scoring scoring) {
        table.addRow().addContent(
            scoring.getItemId(),
            scoring.getMetadata().getName(),
            scoring.getMetadata().getDescription(),
            scoring.getMetadata().isEnabled() ? "Enabled" : "Disabled",
            scoring.getScope(),
            scoring.getElements() != null ? scoring.getElements().size() : 0
        );
    }
} 