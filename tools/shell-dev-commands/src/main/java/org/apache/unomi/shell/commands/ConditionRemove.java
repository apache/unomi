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
package org.apache.unomi.shell.commands;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.services.DefinitionsService;

@Command(scope = "unomi", name = "condition-remove", description = "This command will remove an condition type.")
@Service
public class ConditionRemove extends RemoveCommandSupport {

    @Reference
    DefinitionsService definitionsService;

    @Argument(index = 0, name = "conditionId", description = "The identifier for the condition", required = true, multiValued = false)
    String conditionTypeIdentifier;

    @Override
    public Object doRemove() throws Exception {
        definitionsService.removeConditionType(conditionTypeIdentifier);
        return true;
    }

    @Override
    public String getResourceDescription() {
        return "condition type [" + conditionTypeIdentifier + "]";
    }
}
