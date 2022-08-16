import org.apache.unomi.shell.migration.actions.MigrationHistory
import org.apache.unomi.shell.migration.utils.ConsoleUtils

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

MigrationHistory history = migrationHistory
history.performMigrationStep("step 1", () -> {
    ConsoleUtils.printMessage(session, "inside step 1")
})

history.performMigrationStep("step 2", () -> {
    ConsoleUtils.printMessage(session, "inside step 2")
})

history.performMigrationStep("step 3", () -> {
    ConsoleUtils.printMessage(session, "inside step 3")
    throw new RuntimeException("Intentional failure !")
})

history.performMigrationStep("step 4", () -> {
    ConsoleUtils.printMessage(session, "inside step 4")
})

history.performMigrationStep("step 5", () -> {
    ConsoleUtils.printMessage(session, "inside step 5")
})