package com.team22.eventticketing.sales.mongo;

import java.util.Map;

public class EventFactory {

    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        return switch (type) {
            case AUTH           -> buildAuthEvent(params);
            case EVENT_ACTIVITY -> buildEventActivityEvent(params);
            case BOOKING        -> buildBookingEvent(params);
            case TICKET         -> buildTicketEvent(params);
            case PAYMENT_AUDIT  -> buildPaymentAuditEvent(params);
        };
    }

    private static AuthEvent buildAuthEvent(Map<String, Object> params) {
        String action = (String) params.get("action");
        Long userId = params.get("userId") != null ? ((Number) params.get("userId")).longValue() : null;
        String email = (String) params.get("email");
        return new AuthEvent(action, params, userId, email);
    }

    private static EventActivityEvent buildEventActivityEvent(Map<String, Object> params) {
        String action = (String) params.get("action");
        Long eventId = params.get("eventId") != null ? ((Number) params.get("eventId")).longValue() : null;
        return new EventActivityEvent(action, params, eventId);
    }

    private static BookingEvent buildBookingEvent(Map<String, Object> params) {
        String action = (String) params.get("action");
        Long bookingId = params.get("bookingId") != null ? ((Number) params.get("bookingId")).longValue() : null;
        return new BookingEvent(action, params, bookingId);
    }

    private static TicketEvent buildTicketEvent(Map<String, Object> params) {
        String action = (String) params.get("action");
        Long ticketId = params.get("ticketId") != null ? ((Number) params.get("ticketId")).longValue() : null;
        return new TicketEvent(action, params, ticketId);
    }

    private static PaymentAuditEvent buildPaymentAuditEvent(Map<String, Object> params) {
        String action = (String) params.get("action");
        String method = (String) params.get("method");
        Double amount = params.get("amount") != null ? ((Number) params.get("amount")).doubleValue() : null;
        Long saleId = params.get("saleId") != null ? ((Number) params.get("saleId")).longValue() : null;
        return new PaymentAuditEvent(saleId, action, params, method, amount);
    }
}
