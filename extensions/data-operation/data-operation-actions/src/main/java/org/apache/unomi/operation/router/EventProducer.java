package org.apache.unomi.operation.router;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.unomi.api.Event;

public interface EventProducer {
    void send(Event e);
}
