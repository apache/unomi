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

import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.util.GroovyScriptEngine;
import org.apache.unomi.groovy.actions.GroovyAction;

/**
 * A service to load groovy files and manage {@link GroovyAction}
 */
public interface GroovyActionsService {

    /**
     * Save a groovy action from a groovy file
     *
     * @param fileName     fileName
     * @param groovyScript script to save
     */
    void save(String fileName, String groovyScript);

    /**
     * Remove a groovy action
     *
     * @param id of the action to remove
     * @return true if the action was successfully deleted, false otherwise
     */
    boolean remove(String id);

    /**
     * Get a groovy code source object by an id
     *
     * @param id of the action to get
     * @return Groovy code source
     */
    GroovyCodeSource getGroovyCodeSource(String id);

    /**
     * Get an instantiated groovy shell object
     *
     * @return GroovyShell
     */
    GroovyShell getGroovyShell();
}
