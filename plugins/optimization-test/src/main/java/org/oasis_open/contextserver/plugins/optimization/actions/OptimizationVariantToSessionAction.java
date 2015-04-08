package org.oasis_open.contextserver.plugins.optimization.actions;

/*
 * #%L
 * Context Server Plugin - Provides conditions for events that need to be tracked
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.CustomItem;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

/**
 * Set variant id on the session when optimization test event is triggered
 */
public class OptimizationVariantToSessionAction implements ActionExecutor{
    @Override
    public boolean execute(Action action, Event event) {
        Session session = event.getSession();
        if (session == null) {
            return false;
        }

        // we know that the source and target are CustomItem
        if (!(event.getTarget() instanceof CustomItem)){
            return false;
        }
        CustomItem optimizationTestItem = (CustomItem) event.getTarget();

        session.setProperty("optimizationTest_" + optimizationTestItem.getItemId(), optimizationTestItem.getProperties().get("variantId"));
        return true;
    }
}
