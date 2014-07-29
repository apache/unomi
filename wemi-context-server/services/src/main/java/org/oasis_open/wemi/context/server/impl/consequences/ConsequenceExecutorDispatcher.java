package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class ConsequenceExecutorDispatcher {

    private Map<String, AbstractConsequenceExecutor> visitors = new HashMap<String, AbstractConsequenceExecutor>();

    private User user;
    private boolean changed = false;

    public ConsequenceExecutorDispatcher(User user) {
        this.user = user;

        // @todo remove this hardcoding and replace it with a proper list of consequences coming from the rule
        addVisitor(new SetPropertyConsequence());
    }

    public void addVisitor(AbstractConsequenceExecutor visitor) {
        visitors.put(visitor.getConsequenceId(), visitor);
    }

    public void execute(Consequence consequence) {
        changed |= visitors.get(consequence.getConsequenceType().getId()).execute(consequence, user);
    }

    public boolean isChanged() {
        return changed;
    }
}
