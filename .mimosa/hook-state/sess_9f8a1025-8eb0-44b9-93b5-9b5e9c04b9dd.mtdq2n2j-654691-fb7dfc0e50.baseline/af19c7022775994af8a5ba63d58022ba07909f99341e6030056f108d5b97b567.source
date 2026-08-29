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

package org.apache.unomi.api.actions;

import org.apache.unomi.api.rules.Rule;

/**
 * Hook executed after all rule actions for an event have run.
 * Use post-executors for cleanup, logging, or follow-up work that must
 * happen once per event after the main action chain completes.
 */
public interface ActionPostExecutor {
    /**
     * Performs the action.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    boolean execute();
}
