// ABOUTME: Test component that records every application event of the given types, from any thread.
// ABOUTME: Lets integration tests assert on events published by asynchronous module listeners.
package dev.thinkahead.sepa;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.event.EventListener;

public class CapturedEvents {

    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener
    void on(Object event) {
        events.add(event);
    }

    public <T> List<T> ofType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
