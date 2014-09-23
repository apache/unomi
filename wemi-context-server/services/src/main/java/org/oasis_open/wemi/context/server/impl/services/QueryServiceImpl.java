package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.QueryService;
import org.oasis_open.wemi.context.server.persistence.spi.Aggregate;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import java.util.Map;

/**
 * Created by toto on 23/09/14.
 */
public class QueryServiceImpl implements QueryService {
    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void postConstruct() {
    }

    public void preDestroy() {
    }



    @Override
    public Map<String, Long> getAggregate(String itemType, String property) {
        return persistenceService.aggregateQuery(null, new Aggregate(Aggregate.Type.TERMS, property), itemType);
    }

    @Override
    public Map<String, Long> getAggregate(String type, String property, Condition filter) {
        ParserHelper.resolveConditionType(definitionsService,filter);
        return persistenceService.aggregateQuery(filter, new Aggregate(Aggregate.Type.TERMS, property), type);
    }
}
