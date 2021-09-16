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
package org.apache.unomi.groovy.actions.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is designed to describe the groovy actions which are created from groovy file, the informations added with this
 * annotation will be processed to create an action type entry in elastic search.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Action {

    /**
     * @return id of the action
     */
    String id();

    /**
     * @return name of the action
     */
    String name() default "";

    /**
     * @return description of the action
     */
    String description() default "";

    /**
     * Action Executor to allow to define which action will be called by the action dispatcher
     * The groovy action have to be prefixed by groovy:
     * @return actionExecutor of the action
     */
    String actionExecutor();

    /**
     * @return action is hidden
     */
    boolean hidden() default false;

    /**
     * Parameters specific to the action
     * The value of the parameters can be retrieved in the action like the following:
     * action.getParameterValues().get(<parameter name>);
     * @return parameters
     */
    Parameter[] parameters() default {};

    /**
     * List of tags that help to classify the action
     * @return systemTags
     */
    String[] systemTags() default {};
}
