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

import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.schema.CDPEventInterfaceRegister;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(immediate = true, service = UnomiEventPublisher.class)
public class UnomiEventPublisher implements EventListenerService {

    private ServiceRegistration<?> eventServiceRegistration;

    private List<EventPublisherListener> listeners = new CopyOnWriteArrayList<>();

    private CDPEventInterfaceRegister eventRegister;

    private PersistenceService persistenceService;

    @Activate
    public void activate(BundleContext bundleContext) {
        final String[] interfaces = Arrays.stream(UnomiEventPublisher.class.getInterfaces()).map(Class::getName).toArray(String[]::new);
        eventServiceRegistration = bundleContext.registerService(interfaces, this, new Hashtable<>());
    }

    @Deactivate
    public void deactivate() {
        if (eventServiceRegistration != null) {
            eventServiceRegistration.unregister();
        }
    }

    @Reference
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Reference
    public void setEventInterfaceRegister(CDPEventInterfaceRegister eventRegister) {
        this.eventRegister = eventRegister;
    }

    public UnomiEventPublisher() {
    }

    public Publisher<CDPEventInterface> createPublisher() {
        return createPublisher(null);
    }

    public Publisher<CDPEventInterface> createPublisher(Condition filterCondition) {

        EventPublisherListener listener = new EventPublisherListener(filterCondition);

        return Observable
                .create((ObservableEmitter<CDPEventInterface> emitter) -> {
                    listener.setEmitter(emitter);
                    this.addListener(listener);
                })
                .doFinally(() -> this.removeListener(listener))
                .toFlowable(BackpressureStrategy.BUFFER);
    }

    @Override
    public boolean canHandle(Event event) {
        return true;
    }

    @Override
    public int onEvent(Event event) {
        if (!event.isPersistent()) {
            return EventService.NO_CHANGE;
        }

        listeners.forEach((listener) -> {
            if (listener.getCondition() == null || persistenceService.testMatch(listener.getCondition(), event)) {
                listener.getEmitter().onNext(eventRegister.getEvent(event));
            }
        });

        return EventService.NO_CHANGE;
    }

    public boolean addListener(final EventPublisherListener listener) {
        return this.listeners.add(listener);
    }

    public boolean removeListener(final EventPublisherListener listener) {
        return this.listeners.remove(listener);
    }

    class EventPublisherListener {

        private ObservableEmitter<CDPEventInterface> emitter;

        private Condition condition;

        EventPublisherListener(Condition condition) {
            this.condition = condition;
        }

        public void setEmitter(ObservableEmitter<CDPEventInterface> emitter) {
            this.emitter = emitter;
        }

        public void setCondition(Condition condition) {
            this.condition = condition;
        }

        public ObservableEmitter<CDPEventInterface> getEmitter() {
            return emitter;
        }

        public Condition getCondition() {
            return condition;
        }
    }
}
