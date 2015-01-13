package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.query.AggregateQuery;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.QueryService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.DateAggregate;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;

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
    public Map<String, Long> getAggregate(String type, String property) {
        return persistenceService.aggregateQuery(null, new TermsAggregate(property), type);
    }

    @Override
    public Map<String, Long> getAggregate(String type, String property, AggregateQuery query) {
        if(query != null) {
            // resolve condition
            if(query.getCondition() != null){
                ParserHelper.resolveConditionType(definitionsService, query.getCondition());
            }

            // resolve aggregate
            if(query.getAggregate() != null) {
                if (query.getAggregate().getType() != null){
                    // try to guess the aggregate type
                    if(query.getAggregate().getType().equals("date")){
                        String interval = (String) query.getAggregate().getParameters().get("interval");
                        return persistenceService.aggregateQuery(query.getCondition(), new DateAggregate(property, interval), type);
                    }
                }
            }

            // fall back on terms aggregate
            return persistenceService.aggregateQuery(query.getCondition(), new TermsAggregate(property), type);
        }

        return getAggregate(type, property);
    }
}
