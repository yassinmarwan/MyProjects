package com.team22.eventticketing.user.observer;

public interface EntityObserver {
    void onEvent(String eventType, Object payload);
}
