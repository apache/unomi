package org.apache.unomi.persistence.opensearch;

import org.apache.unomi.persistence.spi.QueryBuilderAvailabilityTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class OpenSearchQueryBuilderAvailabilityTracker implements QueryBuilderAvailabilityTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchQueryBuilderAvailabilityTracker.class.getName());

    private final Set<String> availableQueryBuilders = ConcurrentHashMap.newKeySet();
    private final Set<String> requiredQueryBuilders = ConcurrentHashMap.newKeySet();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition allBuildersAvailable = lock.newCondition();

    public OpenSearchQueryBuilderAvailabilityTracker() {
        // Initialize required query builders
        requiredQueryBuilders.add("propertyConditionOSQueryBuilder");
        requiredQueryBuilders.add("booleanConditionOSQueryBuilder");
        requiredQueryBuilders.add("notConditionOSQueryBuilder");
        requiredQueryBuilders.add("matchAllConditionOSQueryBuilder");
        requiredQueryBuilders.add("idsConditionOSQueryBuilder");
        requiredQueryBuilders.add("geoLocationByPointSessionConditionOSQueryBuilder");
        requiredQueryBuilders.add("sourceEventPropertyConditionOSQueryBuilder");
        requiredQueryBuilders.add("pastEventConditionOSQueryBuilder");
        requiredQueryBuilders.add("nestedConditionOSQueryBuilder");
    }

    public void bindQueryBuilder(ConditionOSQueryBuilder queryBuilder, java.util.Map<String, Object> properties) {
        String queryBuilderId = (String) properties.get("queryBuilderId");
        if (queryBuilderId != null) {
            LOGGER.info("Registering query builder: {}", queryBuilderId);
            availableQueryBuilders.add(queryBuilderId);
            checkAndSignalAvailability();
        }
    }

    public void unbindQueryBuilder(ConditionOSQueryBuilder queryBuilder, java.util.Map<String, Object> properties) {
        String queryBuilderId = (String) properties.get("queryBuilderId");
        if (queryBuilderId != null) {
            LOGGER.info("Unregistering query builder: {}", queryBuilderId);
            availableQueryBuilders.remove(queryBuilderId);
        }
    }

    @Override
    public boolean areAllQueryBuildersAvailable() {
        return availableQueryBuilders.containsAll(requiredQueryBuilders);
    }

    @Override
    public Set<String> getAvailableQueryBuilderIds() {
        return Set.copyOf(availableQueryBuilders);
    }

    @Override
    public Set<String> getMissingQueryBuilderIds() {
        Set<String> missing = ConcurrentHashMap.newKeySet();
        missing.addAll(requiredQueryBuilders);
        missing.removeAll(availableQueryBuilders);
        return missing;
    }

    @Override
    public boolean waitForQueryBuilders(long timeout) throws InterruptedException {
        lock.lock();
        try {
            long startTime = System.currentTimeMillis();
            while (!areAllQueryBuildersAvailable()) {
                long remainingTime = timeout - (System.currentTimeMillis() - startTime);
                if (remainingTime <= 0) {
                    return false;
                }
                if (!allBuildersAvailable.await(remainingTime, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void checkAndSignalAvailability() {
        if (areAllQueryBuildersAvailable()) {
            lock.lock();
            try {
                allBuildersAvailable.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
} 