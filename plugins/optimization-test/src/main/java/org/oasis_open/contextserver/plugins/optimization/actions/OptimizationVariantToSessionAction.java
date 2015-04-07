package org.oasis_open.contextserver.plugins.optimization.actions;

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
