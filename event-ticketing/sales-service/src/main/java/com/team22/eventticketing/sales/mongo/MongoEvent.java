package com.team22.eventticketing.sales.mongo;

import java.time.Instant;
import java.util.Map;

public interface MongoEvent {
    String getId();
    Instant getTimestamp();
    String getAction();
    Map<String, Object> getDetails();
}
