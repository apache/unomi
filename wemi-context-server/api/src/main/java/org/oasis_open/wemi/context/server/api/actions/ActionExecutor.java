package org.oasis_open.wemi.context.server.api.actions;

import org.oasis_open.wemi.context.server.api.Event;

/**
 * The common interface for all action executors
 */
public interface ActionExecutor {

    public abstract boolean execute(Action action, Event event);

}
