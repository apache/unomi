package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;

/**
 * Created by toto on 27/06/14.
 */
public class ActionExecutorDispatcher {

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public ActionExecutorDispatcher() {

    }

    public boolean execute(Action action, Event event) {
        Collection<ServiceReference<ActionExecutor>> matchingActionExecutorReferences;
        if (action.getActionType().getServiceFilter() == null) {
            throw new UnsupportedOperationException("No service defined for : "+action.getActionType());
        }
        try {
            matchingActionExecutorReferences = bundleContext.getServiceReferences(ActionExecutor.class, action.getActionType().getServiceFilter());
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
            return false;
        }
        boolean changed = false;
        for (ServiceReference<ActionExecutor> actionExecutorReference : matchingActionExecutorReferences) {
            ActionExecutor actionExecutor = bundleContext.getService(actionExecutorReference);
            changed |= actionExecutor.execute(action, event);
        }
        return changed;
    }

}
