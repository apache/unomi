package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;

/**
 * Created by toto on 27/06/14.
 */
public class ConsequenceExecutorDispatcher {

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public ConsequenceExecutorDispatcher() {

    }

    public boolean execute(Consequence consequence, Event event) {
        Collection<ServiceReference<ConsequenceExecutor>> matchingConsequenceExecutorReferences;
        try {
            matchingConsequenceExecutorReferences = bundleContext.getServiceReferences(ConsequenceExecutor.class, consequence.getConsequenceType().getServiceFilter());
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
            return false;
        }
        boolean changed = false;
        for (ServiceReference<ConsequenceExecutor> consequenceExecutorReference : matchingConsequenceExecutorReferences) {
            ConsequenceExecutor consequenceExecutor = bundleContext.getService(consequenceExecutorReference);
            changed |= consequenceExecutor.execute(consequence, event);
        }
        return changed;
    }

}
