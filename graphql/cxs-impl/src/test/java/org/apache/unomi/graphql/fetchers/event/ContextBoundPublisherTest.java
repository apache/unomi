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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.security.auth.Subject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * A subscription's selection set is executed on the thread that produced the event, not on the thread
 * that registered the subscription. These tests pin the property that makes that safe: the subscriber's
 * own identity is bound for the duration of each delivery, and always unbound afterwards so it cannot be
 * inherited by unrelated work on that shared producer thread.
 */
@ExtendWith(MockitoExtension.class)
class ContextBoundPublisherTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private ExecutionContextManager executionContextManager;

    private final Subject subject = new Subject();
    private final ExecutionContext executionContext = new ExecutionContext("tenant-a", null, null);

    @Test
    void delivery_bindsSubscriberIdentityThenUnbinds() {
        final List<String> observed = new ArrayList<>();
        final Publisher<String> source = subscriber -> {
            subscriber.onSubscribe(noopSubscription());
            subscriber.onNext("event");
        };

        new ContextBoundPublisher<>(source, subject, executionContext, securityService, executionContextManager)
                .subscribe(recordingSubscriber(observed));

        assertEquals(1, observed.size());
        // Bound before the delivery reached the downstream subscriber...
        verify(securityService).setCurrentSubject(subject);
        verify(executionContextManager).setCurrentContext(executionContext);
        // ...and unbound afterwards.
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
    }

    @Test
    void delivery_unbindsEvenWhenDownstreamThrows() {
        final Publisher<String> source = subscriber -> {
            subscriber.onSubscribe(noopSubscription());
            try {
                subscriber.onNext("event");
            } catch (RuntimeException expected) {
                // the downstream failure is not what this test asserts on
            }
        };

        new ContextBoundPublisher<>(source, subject, executionContext, securityService, executionContextManager)
                .subscribe(throwingSubscriber());

        // A leaked identity on a pooled producer thread would be worse than the failure itself.
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
    }

    @Test
    void delivery_bindsBeforeDownstreamAndClearsAfter() {
        final Publisher<String> source = subscriber -> {
            subscriber.onSubscribe(noopSubscription());
            subscriber.onNext("event");
        };

        new ContextBoundPublisher<>(source, subject, executionContext, securityService, executionContextManager)
                .subscribe(recordingSubscriber(new ArrayList<>()));

        inOrder(securityService).verify(securityService).setCurrentSubject(subject);
        inOrder(securityService).verify(securityService).clearCurrentSubject();
    }

    private Subscription noopSubscription() {
        return new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };
    }

    private Subscriber<String> recordingSubscriber(final List<String> sink) {
        return new BaseSubscriber() {
            @Override
            public void onNext(String item) {
                sink.add(item);
            }
        };
    }

    private Subscriber<String> throwingSubscriber() {
        return new BaseSubscriber() {
            @Override
            public void onNext(String item) {
                throw new IllegalStateException("downstream failure");
            }
        };
    }

    private abstract static class BaseSubscriber implements Subscriber<String> {
        @Override
        public void onSubscribe(Subscription subscription) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }
}
