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
package org.apache.unomi.shell.dev.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Option;

import java.io.IOException;

public abstract class RemoveCommandSupport extends BaseSimpleCommand {

    @Option(name = "--force", description = "Force deletion without confirmation", required = false, multiValued = false)
    boolean force;

    public abstract Object doRemove() throws Exception;

    public abstract String getResourceDescription();

    @Override
    public Object execute() throws Exception {
        Object result = null;
        // Prompt for confirmation
        if (force || askForConfirmation("Are you sure you want to delete "+getResourceDescription()+" ? (yes/no): ")) {
            result = doRemove();
            println("Resource deleted successfully.");
        } else {
            println("Operation cancelled.");
        }
        return result;
    }

    private boolean askForConfirmation(String prompt) throws IOException {
        String input = session.readLine(prompt, null);
        return "yes".equals(input);
    }

}
