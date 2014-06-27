package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.impl.consequences.AbstractConsequenceExecutorVisitor;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceVisitor;
import org.oasis_open.wemi.context.server.impl.consequences.SetPropertyConsequenceVisitor;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class ConsequenceExecutorVisitorDispatcher extends ConsequenceVisitor {

    private Map<String, AbstractConsequenceExecutorVisitor> visitors = new HashMap<String, AbstractConsequenceExecutorVisitor>();

    private User user;
    private boolean changed = false;

    public ConsequenceExecutorVisitorDispatcher(User user) {
        this.user = user;

        addVisitor(new SetPropertyConsequenceVisitor());
    }

    public void addVisitor(AbstractConsequenceExecutorVisitor visitor) {
        visitors.put(visitor.getConsequenceId(), visitor);
    }

    @Override
    public void visit(Consequence consequence) {
        changed |= visitors.get(consequence.getConsequenceType().getId()).visit(consequence, user);
    }

    public boolean isChanged() {
        return changed;
    }
}
