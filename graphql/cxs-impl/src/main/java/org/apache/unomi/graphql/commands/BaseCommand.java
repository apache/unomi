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

import org.apache.unomi.graphql.services.ServiceManager;

public abstract class BaseCommand<T> {

    protected final ServiceManager serviceManager;

    public abstract T execute();

    public BaseCommand(final Builder builder) {
        this.serviceManager = builder.serviceManager;
    }

    public static abstract class Builder<B extends Builder> {

        ServiceManager serviceManager;

        @SuppressWarnings("unchecked")
        public B setServiceManager(ServiceManager serviceManager) {
            this.serviceManager = serviceManager;
            return (B) this;
        }

    }

}