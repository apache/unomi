package org.apache.unomi.operation.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.operation.router.EventProducer;

public class BufferEventProcessingAction implements ActionExecutor {
    private EventProducer producer;

    @Override
    public int execute(Action action, Event event) {
        this.producer.send(event);
        return EventService.NO_CHANGE;
    }

    public void setProducer(EventProducer producer) {
        this.producer = producer;
    }

    public EventProducer getProducer() {
        return producer;
    }
}
