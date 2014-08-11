package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.RulesService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.Set;

@OsgiServiceProvider
public class RulesServiceOsgi implements RulesService, EventListenerService, BundleListener {

    @Inject
    private RulesServiceImpl rulesService;

    @Override
    public void removeRule(String ruleId) {
        rulesService.removeRule(ruleId);
    }

    @PostConstruct
    public void postConstruct() {
    }

    @PreDestroy
    public void preDestroy() {
        rulesService.preDestroy();
    }

    public Set<Rule> getMatchingRules(Event event) {
        return rulesService.getMatchingRules(event);
    }

    public boolean canHandle(Event event) {
        return rulesService.canHandle(event);
    }

    public boolean onEvent(Event event) {
        return rulesService.onEvent(event);
    }

    public void bundleChanged(BundleEvent event) {
        rulesService.bundleChanged(event);
    }

    @Override
    public Set<Metadata> getRuleMetadatas() {
        return rulesService.getRuleMetadatas();
    }

    @Override
    public Rule getRule(String ruleId) {
        return rulesService.getRule(ruleId);
    }

    @Override
    public void setRule(String ruleId, Rule rule) {
        rulesService.setRule(ruleId, rule);
    }

    @Override
    public void createRule(String ruleId, String name, String description) {
        rulesService.createRule(ruleId, name, description);
    }

    public static RulesService getInstance() {
        return RulesServiceImpl.getInstance();
    }
}
