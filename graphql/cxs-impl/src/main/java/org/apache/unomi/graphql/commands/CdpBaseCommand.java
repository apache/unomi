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

import org.apache.unomi.graphql.services.CDPServiceManager;

public abstract class CdpBaseCommand<T> {

    final CDPServiceManager cdpServiceManager;

    public abstract T execute();

    public CdpBaseCommand(final Builder builder) {
        this.cdpServiceManager = builder.cdpServiceManager;
    }

    public static abstract class Builder<B extends Builder> {

        CDPServiceManager cdpServiceManager;

        @SuppressWarnings("unchecked")
        public B setCdpServiceService(CDPServiceManager cdpServiceManager) {
            this.cdpServiceManager = cdpServiceManager;
            return (B) this;
        }

    }

}
