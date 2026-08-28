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
package org.apache.unomi.graphql.commands;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.services.ServiceManager;

import java.util.Objects;

public abstract class BaseCommand<T> {

    protected final DataFetchingEnvironment environment;

    protected final ServiceManager serviceManager;

    public abstract T execute();

    public BaseCommand(final Builder builder) {
        this.environment = builder.environment;
        this.serviceManager = environment.getContext();
    }

    public static abstract class Builder<B extends Builder> {

        protected DataFetchingEnvironment environment;

        @SuppressWarnings("unchecked")
        public B setEnvironment(DataFetchingEnvironment environment) {
            this.environment = environment;
            return (B) this;
        }

        public void validate() {
            Objects.requireNonNull(environment, "Environment can not be null");
        }

    }

}
