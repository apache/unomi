/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.persistence.spi;

/**
 * Interface for tracking the availability of query builders.
 * This allows components to wait for all required query builders to be available
 * before proceeding with operations that depend on them.
 */
public interface QueryBuilderAvailabilityTracker {
    
    /**
     * Checks if all required query builders are available.
     * @return true if all required query builders are available, false otherwise
     */
    boolean areAllQueryBuildersAvailable();
    
    /**
     * Gets the list of currently available query builder IDs.
     * @return set of available query builder IDs
     */
    java.util.Set<String> getAvailableQueryBuilderIds();
    
    /**
     * Gets the list of required query builder IDs that are not yet available.
     * @return set of missing query builder IDs
     */
    java.util.Set<String> getMissingQueryBuilderIds();
    
    /**
     * Waits for all required query builders to become available.
     * @param timeout maximum time to wait in milliseconds
     * @return true if all query builders became available within the timeout, false otherwise
     * @throws InterruptedException if the wait is interrupted
     */
    boolean waitForQueryBuilders(long timeout) throws InterruptedException;
} 