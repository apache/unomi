package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.Metadata;

import java.util.Set;

/**
 * Created by toto on 26/06/14.
 */
public interface RulesService {

    Set<Metadata> getRuleMetadatas();

    Set<Metadata> getRuleMetadatas(String scope);

    Rule getRule(String scope, String ruleId);

    void setRule(Rule rule);

    void removeRule(String scope, String ruleId);

    Set<Condition> getTrackedConditions(Item item);
}
