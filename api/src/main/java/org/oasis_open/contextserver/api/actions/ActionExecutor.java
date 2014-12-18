package org.oasis_open.contextserver.api.actions;

import org.oasis_open.contextserver.api.Event;

/**
 * The common interface for all action executors
 */
public interface ActionExecutor {

    public abstract boolean execute(Action action, Event event);

}
