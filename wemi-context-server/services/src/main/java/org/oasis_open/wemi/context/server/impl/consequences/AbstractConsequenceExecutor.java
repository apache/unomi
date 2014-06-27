package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

/**
 * Created by toto on 27/06/14.
 */
public abstract class AbstractConsequenceExecutor {

    public abstract String getConsequenceId();

    public abstract boolean execute(Consequence condition, User user);

}
