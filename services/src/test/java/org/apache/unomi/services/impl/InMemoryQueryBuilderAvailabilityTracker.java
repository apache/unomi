package org.apache.unomi.services.impl;

import org.apache.unomi.persistence.spi.QueryBuilderAvailabilityTracker;

import java.util.Collections;
import java.util.Set;

public class InMemoryQueryBuilderAvailabilityTracker implements QueryBuilderAvailabilityTracker {
    @Override
    public boolean areAllQueryBuildersAvailable() {
        return true;
    }

    @Override
    public Set<String> getAvailableQueryBuilderIds() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getMissingQueryBuilderIds() {
        return Collections.emptySet();
    }

    @Override
    public boolean waitForQueryBuilders(long timeout) {
        return true;
    }
} 