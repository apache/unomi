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

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.security.auth.Subject;

/**
 * Binds a subscription's own security identity to every delivery made on its stream.
 * <p>
 * A subscription is registered on the caller's thread, where the subject and execution context are
 * bound, but its selection set is executed later — once per emitted event, on whichever thread
 * produced that event (an event-processing thread shared across tenants). Without this wrapper the
 * selection set therefore runs under whatever identity that producer thread happens to carry, which is
 * not the subscriber's and may belong to another tenant.
 * <p>
 * Wrapping the <em>source</em> publisher is what makes this work: the framework maps source events to
 * responses downstream of this point, so binding here covers the selection-set execution and every
 * fetcher it reaches. The binding is cleared in a {@code finally} on each delivery, so no identity is
 * left behind on a pooled producer thread.
 */
public class ContextBoundPublisher<T> implements Publisher<T> {

    private final Publisher<T> delegate;
    private final Subject subject;
    private final ExecutionContext executionContext;
    private final SecurityService securityService;
    private final ExecutionContextManager executionContextManager;

    public ContextBoundPublisher(final Publisher<T> delegate,
                                 final Subject subject,
                                 final ExecutionContext executionContext,
                                 final SecurityService securityService,
                                 final ExecutionContextManager executionContextManager) {
        this.delegate = delegate;
        this.subject = subject;
        this.executionContext = executionContext;
        this.securityService = securityService;
        this.executionContextManager = executionContextManager;
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        delegate.subscribe(new ContextBoundSubscriber(subscriber));
    }

    private final class ContextBoundSubscriber implements Subscriber<T> {

        private final Subscriber<? super T> delegateSubscriber;

        private ContextBoundSubscriber(final Subscriber<? super T> delegateSubscriber) {
            this.delegateSubscriber = delegateSubscriber;
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            delegateSubscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(final T item) {
            bind();
            try {
                delegateSubscriber.onNext(item);
            } finally {
                clear();
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            bind();
            try {
                delegateSubscriber.onError(throwable);
            } finally {
                clear();
            }
        }

        @Override
        public void onComplete() {
            bind();
            try {
                delegateSubscriber.onComplete();
            } finally {
                clear();
            }
        }

        private void bind() {
            if (securityService != null) {
                securityService.setCurrentSubject(subject);
            }
            if (executionContextManager != null) {
                executionContextManager.setCurrentContext(executionContext);
            }
        }

        private void clear() {
            // Always unbind: this runs on a shared producer thread, so a leaked identity would be
            // inherited by unrelated work scheduled on it afterwards.
            try {
                if (securityService != null) {
                    securityService.clearCurrentSubject();
                }
            } finally {
                if (executionContextManager != null) {
                    executionContextManager.setCurrentContext(null);
                }
            }
        }
    }
}
