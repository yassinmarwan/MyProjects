package com.team22.eventticketing.ticket.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
