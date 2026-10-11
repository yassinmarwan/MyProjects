package com.team22.eventticketing.sales.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
