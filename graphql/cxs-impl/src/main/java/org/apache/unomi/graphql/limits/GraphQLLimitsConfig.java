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
package org.apache.unomi.graphql.limits;

import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bound GraphQL query size and analysis cost. Defaults leave GraphiQL introspection and the public
 * {@code getProfile} / {@code processEvents} fields usable.
 */
@Component(service = GraphQLLimitsConfig.class, immediate = true, configurationPid = "org.apache.unomi.graphql")
@Designate(ocd = GraphQLLimitsConfig.Config.class)
public class GraphQLLimitsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLLimitsConfig.class);

    @ObjectClassDefinition(name = "Apache Unomi GraphQL limits")
    public @interface Config {

        @AttributeDefinition(description = "Maximum GraphQL query string length in characters. Zero or less disables the check.")
        int max_query_length_chars() default 262144;

        @AttributeDefinition(description = "Maximum GraphQL HTTP POST body size in bytes. Zero or less disables the check.")
        int max_request_body_bytes() default 524288;

        @AttributeDefinition(description = "Maximum GraphQL query depth. Zero or less disables the check.")
        int max_query_depth() default 24;

        @AttributeDefinition(description = "Maximum GraphQL query complexity. Zero or less disables the check.")
        int max_query_complexity() default 3000;
    }

    private volatile int maxQueryLengthChars = 262144;
    private volatile int maxRequestBodyBytes = 524288;
    private volatile int maxQueryDepth = 24;
    private volatile int maxQueryComplexity = 3000;

    private volatile GraphQLSchemaUpdater schemaUpdater;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, unbind = "unsetSchemaUpdater")
    protected void setSchemaUpdater(GraphQLSchemaUpdater schemaUpdater) {
        this.schemaUpdater = schemaUpdater;
    }

    protected void unsetSchemaUpdater(GraphQLSchemaUpdater schemaUpdater) {
        if (this.schemaUpdater == schemaUpdater) {
            this.schemaUpdater = null;
        }
    }

    @Activate
    @Modified
    public void activate(Config config) {
        this.maxQueryLengthChars = config.max_query_length_chars();
        this.maxRequestBodyBytes = config.max_request_body_bytes();
        this.maxQueryDepth = config.max_query_depth();
        this.maxQueryComplexity = config.max_query_complexity();
        LOGGER.info("GraphQL limits: length={}, bodyBytes={}, depth={}, complexity={}",
                maxQueryLengthChars, maxRequestBodyBytes, maxQueryDepth, maxQueryComplexity);
        GraphQLSchemaUpdater updater = this.schemaUpdater;
        if (updater != null) {
            updater.updateSchema();
        }
    }

    public int getMaxQueryLengthChars() {
        return maxQueryLengthChars;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public int getMaxQueryDepth() {
        return maxQueryDepth;
    }

    public int getMaxQueryComplexity() {
        return maxQueryComplexity;
    }
}
