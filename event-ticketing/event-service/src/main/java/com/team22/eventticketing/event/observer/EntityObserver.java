package com.team22.eventticketing.event.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
