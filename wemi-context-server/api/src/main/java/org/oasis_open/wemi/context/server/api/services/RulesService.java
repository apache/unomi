package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.rules.Rule;

import java.util.Set;

/**
 * Created by toto on 26/06/14.
 */
public interface RulesService {

    Set<Metadata> getRuleMetadatas();

    Set<Metadata> getRuleMetadatas(String scope);

    Rule getRule(String scope, String ruleId);

    void setRule(Rule rule);

    void createRule(String scope, String ruleId, String name, String description);

    void removeRule(String scope, String ruleId);

}
