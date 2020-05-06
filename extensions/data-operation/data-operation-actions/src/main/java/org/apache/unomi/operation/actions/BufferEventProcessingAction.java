package org.apache.unomi.operation.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.operation.router.EventProducer;

public class BufferEventProcessingAction implements ActionExecutor {
    private EventProducer producer;

    @Override
    public int execute(Action action, Event event) {
        if (validateSchema(action, event)) {
            this.producer.send(event);
        }
        return EventService.NO_CHANGE;
    }

    private boolean validateSchema(Action action, Event event) {
        return event.getItemType().equals(Event.ITEM_TYPE) &&
                event.isPersistent() &&
                !event.getScope().equals(Metadata.SYSTEM_SCOPE);
    }

    public void setProducer(EventProducer producer) {
        this.producer = producer;
    }

    public EventProducer getProducer() {
        return producer;
    }
}
