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
package org.apache.unomi.shell.commands.goals;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.shell.commands.BaseListCommand;

/**
 * Lists all goals in Apache Unomi
 */
@Command(scope = "unomi", name = "goal-list", description = "Lists all goals in Apache Unomi")
@Service
public class GoalListCommand extends BaseListCommand<Goal> {

    @Override
    protected Class<Goal> getItemType() {
        return Goal.class;
    }

    @Override
    protected void printItem(ShellTable table, Goal goal) {
        table.addRow().addContent(
            goal.getItemId(),
            goal.getMetadata().getName(),
            goal.getMetadata().getDescription()
        );
    }
} 