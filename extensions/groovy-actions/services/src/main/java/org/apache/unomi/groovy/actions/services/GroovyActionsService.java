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
package org.apache.unomi.groovy.actions.services;

import groovy.lang.Script;
import org.apache.unomi.groovy.actions.GroovyAction;
import org.apache.unomi.groovy.actions.ScriptMetadata;

/**
 * Service interface for managing Groovy action scripts.
 * <p>
 * This service provides functionality to load, compile, cache, and execute
 * Groovy scripts as actions within the Apache Unomi framework. It implements
 * optimized compilation and caching strategies to achieve high performance.
 * </p>
 *
 * <p>
 * Key features:
 * <ul>
 *   <li>Pre-compilation of scripts at startup</li>
 *   <li>Hash-based change detection for selective recompilation</li>
 *   <li>Thread-safe compilation and execution</li>
 *   <li>Unified caching architecture for compiled scripts</li>
 * </ul>
 * </p>
 *
 * <p>
 * Thread Safety: Implementations must be thread-safe as this service
 * is accessed concurrently during script execution.
 * </p>
 *
 * @see GroovyAction
 * @see ScriptMetadata
 * @since 2.7.0
 */
public interface GroovyActionsService {

    /**
     * Saves a Groovy action script with compilation and validation.
     * <p>
     * This method compiles the script, validates it has the required
     * annotations, persists it, and updates the internal cache.
     * If the script content hasn't changed, recompilation is skipped.
     * </p>
     *
     * @param actionName   the unique identifier for the action
     * @param groovyScript the Groovy script source code
     * @throws IllegalArgumentException if actionName or groovyScript is null
     * @throws RuntimeException if compilation or persistence fails
     */
    void save(String actionName, String groovyScript);

    /**
     * Removes a Groovy action and all associated metadata.
     * <p>
     * This method removes the action from both the cache and persistent storage,
     * and cleans up any registered action types in the definitions service.
     * </p>
     *
     * @param actionName the unique identifier of the action to remove
     * @throws IllegalArgumentException if id is null
     */
    void remove(String actionName);

    /**
     * Retrieves a pre-compiled script class from cache.
     * <p>
     * This is the preferred method for script execution as it returns
     * pre-compiled classes without any compilation overhead. Returns
     * {@code null} if the script is not found in the cache.
     * </p>
     *
     * @param actionName the unique identifier of the action
     * @return the compiled script class, or {@code null} if not found in cache
     * @throws IllegalArgumentException if id is null
     */
    Class<? extends Script> getCompiledScript(String actionName);

    /**
     * Retrieves script metadata for monitoring and change detection.
     * <p>
     * The returned metadata includes content hash, compilation timestamp,
     * and the compiled class reference. This is useful for monitoring
     * tools and debugging.
     * </p>
     *
     * @param actionName the unique identifier of the action
     * @return the script metadata, or {@code null} if not found
     * @throws IllegalArgumentException if actionName is null
     */
    ScriptMetadata getScriptMetadata(String actionName);
}
