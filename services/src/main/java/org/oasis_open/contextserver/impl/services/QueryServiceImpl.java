package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.QueryService;
import org.oasis_open.contextserver.persistence.spi.Aggregate;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;

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
        return persistenceService.aggregateQuery(null, new Aggregate(("timeStamp".equals(property)) ? Aggregate.Type.DATE : Aggregate.Type.TERMS, property), itemType);
    }

    @Override
    public Map<String, Long> getAggregate(String type, String property, Condition filter) {
        ParserHelper.resolveConditionType(definitionsService,filter);
        return persistenceService.aggregateQuery(filter, new Aggregate(("timeStamp".equals(property)) ? Aggregate.Type.DATE : Aggregate.Type.TERMS, property), type);
    }
}
