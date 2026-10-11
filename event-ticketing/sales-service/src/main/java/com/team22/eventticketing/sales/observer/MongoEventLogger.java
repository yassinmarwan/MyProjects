package com.team22.eventticketing.sales.observer;

import com.team22.eventticketing.sales.mongo.EventFactory;
import com.team22.eventticketing.sales.mongo.EventType;
import com.team22.eventticketing.sales.mongo.MongoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final MongoTemplate mongoTemplate;

    public MongoEventLogger(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void onEvent(String action, Object payload) {
        try {
            EventType type = resolveType(action);
            Map<String, Object> params = toParams(action, payload);
            MongoEvent event = EventFactory.createEvent(type, params);
            mongoTemplate.insert(event, resolveCollection(type));
        } catch (Exception e) {
            log.warn("MongoDB event write failed for action {}: {}", action, e.getMessage());
        }
    }

    private EventType resolveType(String action) {
        if (action == null) return EventType.AUTH;
        return switch (action) {
            case "EVENT_CREATED", "EVENT_UPDATED", "EVENT_DETAILS_UPDATED",
                 "EVENT_STATUS_UPDATED", "EVENT_RATED", "SESSION_VERIFIED",
                 "EVENT_DELETED", "SESSION_CREATED", "SESSION_DELETED" -> EventType.EVENT_ACTIVITY;
            case "BOOKING_CREATED", "BOOKING_UPDATED", "BOOKING_DELETED",
                 "BOOKING_COMPLETED", "BOOKING_CANCELLED", "BOOKING_CONFIRMED",
                 "BOOKING_ITEMS_ADDED" -> EventType.BOOKING;
            case "TICKET_CREATED", "TICKET_UPDATED", "TICKET_DELETED",
                 "TICKET_ISSUED", "TICKETS_BATCH_ISSUED", "TICKETS_PURGED" -> EventType.TICKET;
            case "SALE_CREATED", "SALE_UPDATED", "SALE_DELETED", "SALE_REFUNDED",
                 "SALE_FAILED", "SALE_RETRIED", "PAYMENT_PROCESSED", "PROMOTION_CREATED",
                 "PROMOTION_UPDATED", "PROMOTION_DELETED", "SALE_PROMOTION_LINKED",
                 "SALE_PROMOTION_UPDATED", "SALE_PROMOTION_DELETED", "PROMOTION_APPLIED",
                 "SALE_REFUNDED_WINDOW_POLICY", "REFUND_DENIED",
                 "ANALYTICS_VIEWED" -> EventType.PAYMENT_AUDIT;
            default -> EventType.AUTH;
        };
    }

    private String resolveCollection(EventType type) {
        return switch (type) {
            case AUTH           -> "auth_events";
            case EVENT_ACTIVITY -> "event_events";
            case BOOKING        -> "booking_events";
            case TICKET         -> "ticket_events";
            case PAYMENT_AUDIT  -> "payment_audit_trail";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toParams(String action, Object payload) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        if (payload instanceof Map) {
            params.putAll((Map<String, Object>) payload);
        } else if (payload != null) {
            params.put("payload", payload);
        }
        return params;
    }
}
