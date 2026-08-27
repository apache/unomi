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
package org.apache.unomi.graphql.fetchers.event;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.condition.factories.EventConditionFactory;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.reactivestreams.Publisher;

import javax.security.auth.Subject;
import java.util.Map;

public class EventListenerSubscriptionFetcher extends BaseDataFetcher<Publisher<CDPEventInterface>> {

    private UnomiEventPublisher eventPublisher;

    public EventListenerSubscriptionFetcher(UnomiEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Publisher<CDPEventInterface> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, Object> filterAsMap = environment.getArgument("filter");

        final Publisher<CDPEventInterface> publisher;
        if (filterAsMap == null) {
            publisher = eventPublisher.createPublisher();
        } else {
            final CDPEventFilterInput filterInput = CDPEventFilterInput.fromMap(filterAsMap);
            final Condition filterCondition = EventConditionFactory.get(environment).eventFilterInputCondition(filterInput, filterAsMap);

            publisher = eventPublisher.createPublisher(filterCondition);
        }

        return bindToSubscriberIdentity(publisher, environment);
    }

    /**
     * Captures the identity this subscription is being created under, while it is still bound to this
     * thread, and carries it onto every later delivery. Events are emitted from a producer thread that
     * carries no identity of this subscriber's, so without this the selection set would execute under
     * whatever identity that thread happened to hold.
     */
    private Publisher<CDPEventInterface> bindToSubscriberIdentity(
            final Publisher<CDPEventInterface> publisher, final DataFetchingEnvironment environment) {
        final Object context = environment.getContext();
        if (!(context instanceof ServiceManager)) {
            return publisher;
        }
        final ServiceManager serviceManager = (ServiceManager) context;
        final SecurityService securityService = serviceManager.getService(SecurityService.class);
        final ExecutionContextManager executionContextManager = serviceManager.getService(ExecutionContextManager.class);
        if (securityService == null && executionContextManager == null) {
            return publisher;
        }

        final Subject subject = securityService != null ? securityService.getCurrentSubject() : null;
        final ExecutionContext executionContext =
                executionContextManager != null ? executionContextManager.getCurrentContext() : null;

        return new ContextBoundPublisher<>(publisher, subject, executionContext, securityService, executionContextManager);
    }
}
