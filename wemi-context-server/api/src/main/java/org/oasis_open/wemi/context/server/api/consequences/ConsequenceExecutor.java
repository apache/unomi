package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * The common interface for all consequence executors
 */
public interface ConsequenceExecutor {

    public abstract boolean execute(Consequence consequence, Event event);

}
