package com.team22.eventticketing.ticket.service;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team22.eventticketing.ticket.adapter.CassandraRowAdapter;
import com.team22.eventticketing.ticket.adapter.ObjectArrayDtoAdapter;
import com.team22.eventticketing.ticket.adapter.PostgresRowAdapter;

import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketScanEvent;
import com.team22.eventticketing.ticket.model.TicketStatus;
import com.team22.eventticketing.ticket.model.dto.BatchTicketRequestDTO;
import com.team22.eventticketing.ticket.model.dto.EventAttendanceSummaryDTO;
import com.team22.eventticketing.ticket.model.dto.NearbyTicketDTO;
import com.team22.eventticketing.ticket.model.dto.TicketAnalyticsDTO;
import com.team22.eventticketing.ticket.model.dto.TicketItemDTO;
import com.team22.eventticketing.ticket.model.dto.UnusedTicketDTO;
import com.team22.eventticketing.ticket.messaging.TicketEventPublisher;
import com.team22.eventticketing.ticket.observer.EntityObserver;
import com.team22.eventticketing.ticket.repository.BookingEventMappingRepository;
import com.team22.eventticketing.ticket.repository.TicketRepository;
import com.team22.eventticketing.ticket.resilience.BookingServiceFacade;

import com.team22.eventticketing.contracts.dto.EventDTO;
import com.team22.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import com.team22.eventticketing.contracts.events.TicketStatusChangedEvent;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.contracts.dto.VenueCoordsDTO;
import com.team22.eventticketing.contracts.dto.BookingDTO;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TicketService {

        private final TicketRepository ticketRepository;
        private final CacheEvictionService cacheEviction;
        private final List<EntityObserver> observers = new ArrayList<>();
        private final BookingServiceFacade bookingServiceFacade;
        private final EventServiceClient eventServiceClient;
        private static final Logger log = LoggerFactory.getLogger(TicketService.class);
        private final TicketEventPublisher ticketEventPublisher;
        private final BookingEventMappingRepository bookingEventMappingRepository;

        public TicketService(TicketRepository ticketRepository, CacheEvictionService cacheEviction,
                        BookingServiceFacade bookingServiceFacade, EventServiceClient eventServiceClient,
                        TicketEventPublisher ticketEventPublisher,
                        BookingEventMappingRepository bookingEventMappingRepository) {
                this.ticketRepository = ticketRepository;
                this.cacheEviction = cacheEviction;
                this.bookingServiceFacade = bookingServiceFacade;
                this.eventServiceClient = eventServiceClient;
                this.ticketEventPublisher = ticketEventPublisher;
                this.bookingEventMappingRepository = bookingEventMappingRepository;
        }

        private Long resolveEventId(Long bookingId, BookingDTO bookingFromFeign) {
                return bookingEventMappingRepository.findById(bookingId)
                                .map(m -> m.getEventId())
                                .orElseGet(() -> {
                                        Long feignEventId = bookingFromFeign.eventId();
                                        if (feignEventId == null)
                                                return null;
                                        return feignEventId;
                                });
        }

        public void register(EntityObserver observer) {
                observers.add(observer);
        }

        public void unregister(EntityObserver observer) {
                observers.remove(observer);
        }

        private void notifyObservers(String action, Object payload) {
                for (EntityObserver o : observers)
                        o.onEvent(action, payload);
        }

        // ─── CRUD
        // ─────────────────────────────────────────────────────────────────────
        @CacheEvict(value = "ticket-service::S4-F10", allEntries = true)
        public Ticket createTicket(Ticket ticket) {
                long start = System.currentTimeMillis();

                Ticket saved = ticketRepository.save(ticket);

                MDC.put("ticketId", saved.getId().toString());

                log.info(
                                "{} {} saved with status={}",
                                "Ticket",
                                saved.getId(),
                                saved.getStatus());

                notifyObservers("TICKET_CREATED", Map.of("ticketId", saved.getId()));

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "createTicket",
                                        elapsed);
                }

                MDC.remove("ticketId");

                return saved;
        }

        @Cacheable(cacheNames = "ticket-service::ticket", key = "#id")
        public Ticket getTicketById(Long id) {
                MDC.put("ticketId", id.toString());

                Ticket ticket = ticketRepository.findById(id)
                                .orElseThrow(
                                                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                                "Ticket not found with id: " + id));

                log.info(
                                "TICKET_FOUND ticketId={} status={}",
                                ticket.getId(),
                                ticket.getStatus());

                MDC.remove("ticketId");

                return ticket;
        }

        public List<Ticket> getAllTickets() {
                List<Ticket> tickets = ticketRepository.findAll(); // list — NOT cached

                log.info(
                                "TICKETS_LISTED count={}",
                                tickets.size());

                return tickets;
        }

        @CacheEvict(value = "ticket-service::S4-F10", allEntries = true)
        public Ticket updateTicket(Long id, Ticket updated) {
                long start = System.currentTimeMillis();

                MDC.put("ticketId", id.toString());

                Ticket existing = getTicketById(id);
                TicketStatus previousStatus = existing.getStatus();

                if (updated.getBookingId() != null)
                        existing.setBookingId(updated.getBookingId());
                if (updated.getAttendeeName() != null)
                        existing.setAttendeeName(updated.getAttendeeName());
                if (updated.getTicketCode() != null)
                        existing.setTicketCode(updated.getTicketCode());
                if (updated.getStatus() != null)
                        existing.setStatus(updated.getStatus());
                if (updated.getIssuedAt() != null)
                        existing.setIssuedAt(updated.getIssuedAt());
                if (updated.getMetadata() != null)
                        existing.setMetadata(updated.getMetadata());

                Ticket saved = ticketRepository.save(existing);

                log.info(
                                "TICKET_UPDATED ticketId={} status={}",
                                saved.getId(),
                                saved.getStatus());

                cacheEviction.evictEntityAndFeatures("ticket", id, "S4-F1", "S4-F5", "S4-F6", "S4-F8", "S4-F9",
                                "S4-F10");

                if (saved.getStatus() != null && saved.getStatus() != previousStatus) {
                        ticketEventPublisher.publishTicketStatusChanged(
                                        new TicketStatusChangedEvent(saved.getId(), saved.getBookingId(),
                                                        saved.getStatus().name()));
                }

                notifyObservers("TICKET_UPDATED", Map.of("ticketId", saved.getId()));

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "updateTicket",
                                        elapsed);
                }

                MDC.remove("ticketId");

                return saved;
        }

        public void deleteTicket(Long id) {
                long start = System.currentTimeMillis();

                MDC.put("ticketId", id.toString());

                Ticket existing = getTicketById(id);
                ticketRepository.delete(existing);

                log.info(
                                "TICKET_DELETED ticketId={}",
                                id);

                cacheEviction.evictEntityAndFeatures("ticket", id, "S4-F1", "S4-F5", "S4-F6", "S4-F8", "S4-F9",
                                "S4-F10");
                notifyObservers("TICKET_DELETED", Map.of("ticketId", id));

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "deleteTicket",
                                        elapsed);
                }

                MDC.remove("ticketId");
        }

        @Transactional
        public int batchIssueTickets(BatchTicketRequestDTO request) {

                long start = System.currentTimeMillis();

                Long bookingId = request.getBookingId();

                MDC.put("bookingId", bookingId.toString());

                List<TicketItemDTO> items = request.getTickets();

                log.info(
                                "Calling {}.{} with args={}",
                                "BookingServiceClient",
                                "getBooking",
                                bookingId);

                BookingDTO booking;

                try {

                        booking = bookingServiceFacade.getBooking(bookingId);

                        log.info(
                                        "{}.{} returned successfully",
                                        "BookingServiceClient",
                                        "getBooking");

                } catch (Exception e) {

                        log.warn(
                                        "Feign call to {} failed: {}",
                                        "booking-service",
                                        e.getMessage());

                        MDC.remove("bookingId");

                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Booking not found with id: " + bookingId);
                }

                // M3: resolve eventId from booking.placed mapping (Strategy A) — fall back to
                // the Feign response if the mapping hasn't been consumed yet
                Long eventId = resolveEventId(bookingId, booking);

                MDC.put("eventId", eventId.toString());

                Set<String> codesInBatch = new HashSet<>();

                for (TicketItemDTO item : items) {

                        if (!codesInBatch.add(item.getTicketCode())) {

                                log.warn(
                                                "DUPLICATE_BATCH_TICKET_CODE bookingId={} ticketCode={}",
                                                bookingId,
                                                item.getTicketCode());

                                MDC.remove("bookingId");
                                MDC.remove("eventId");

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Duplicate ticket code in batch: "
                                                                + item.getTicketCode());
                        }
                }

                for (String code : codesInBatch) {

                        if (ticketRepository.existsByTicketCode(code)) {

                                log.warn(
                                                "EXISTING_TICKET_CODE_FOUND bookingId={} ticketCode={}",
                                                bookingId,
                                                code);

                                MDC.remove("bookingId");
                                MDC.remove("eventId");

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Ticket code already exists: " + code);
                        }
                }

                LocalDateTime now = LocalDateTime.now();

                List<Ticket> tickets = items.stream().map(item -> {

                        Ticket ticket = new Ticket();

                        ticket.setBookingId(bookingId);

                        ticket.setEventId(eventId);

                        ticket.setAttendeeName(item.getAttendeeName());

                        ticket.setTicketCode(item.getTicketCode());

                        ticket.setStatus(TicketStatus.VALID);

                        ticket.setIssuedAt(now);

                        ticket.setMetadata(item.getMetadata());

                        return ticket;

                }).toList();

                List<Ticket> savedTickets = ticketRepository.saveAll(tickets);

                log.info(
                                "{} {} saved with status={}",
                                "BatchTickets",
                                bookingId,
                                "VALID");

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {

                        log.warn(
                                        "Slow {} took {}ms",
                                        "batchIssueTickets",
                                        elapsed);
                }

                cacheEviction.evictPattern("S4-F1");
                cacheEviction.evictPattern("S4-F5");
                cacheEviction.evictPattern("S4-F6");
                cacheEviction.evictPattern("S4-F9");
                cacheEviction.evictPattern("S4-F10");

                for (Ticket t : savedTickets) {
                        ticketEventPublisher.publishTicketIssued(
                                        new TicketIssuedEvent(t.getId(), bookingId, t.getAttendeeName(),
                                                        t.getTicketCode()));
                }

                notifyObservers(
                                "TICKETS_BATCH_ISSUED",
                                Map.of(
                                                "bookingId", bookingId,
                                                "count", savedTickets.size()));

                MDC.remove("bookingId");
                MDC.remove("eventId");

                return tickets.size();
        }

        @Cacheable(cacheNames = "ticket-service::S4-F1", key = "#bookingId")
        public Ticket getLatestTicketByBooking(Long bookingId) {

                MDC.put("bookingId", bookingId.toString());

                log.info(
                                "Calling {}.{} with args={}",
                                "BookingServiceClient",
                                "getBooking",
                                bookingId);

                try {

                        bookingServiceFacade.getBooking(bookingId);

                        log.info(
                                        "{}.{} returned successfully",
                                        "BookingServiceClient",
                                        "getBooking");

                } catch (Exception e) {

                        log.warn(
                                        "Feign call to {} failed: {}",
                                        "booking-service",
                                        e.getMessage());

                        MDC.remove("bookingId");

                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Booking not found");
                }

                Ticket ticket = ticketRepository
                                .findTopByBookingIdOrderByIssuedAtDesc(bookingId)
                                .orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "No tickets found for this booking"));

                MDC.put("ticketId", ticket.getId().toString());

                log.info(
                                "LATEST_TICKET_FOUND bookingId={} ticketId={}",
                                bookingId,
                                ticket.getId());

                MDC.remove("ticketId");
                MDC.remove("bookingId");

                return ticket;
        }

        // ─── [S4-F2] Issue Ticket (write) ────────────────────────────────────────────
        public Ticket issueTicket(
                        Long bookingId,
                        String attendeeName,
                        String ticketCode,
                        Map<String, Object> metadata) {

                MDC.put("bookingId", bookingId.toString());

                log.info(
                                "Calling {}.{} with args={}",
                                "BookingServiceClient",
                                "getBooking",
                                bookingId);

                BookingDTO booking;

                try {

                        booking = bookingServiceFacade.getBooking(bookingId);

                        log.info(
                                        "{}.{} returned successfully",
                                        "BookingServiceClient",
                                        "getBooking");

                } catch (Exception e) {

                        log.warn(
                                        "Feign call to {} failed: {}",
                                        "booking-service",
                                        e.getMessage());

                        MDC.remove("bookingId");

                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Booking not found");
                }

                Long eventId = booking.eventId();

                MDC.put("eventId", eventId.toString());

                if (ticketRepository.existsByTicketCode(ticketCode)) {

                        log.warn(
                                        "DUPLICATE_TICKET_CODE ticketCode={}",
                                        ticketCode);

                        MDC.remove("bookingId");
                        MDC.remove("eventId");

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Ticket code already exists");
                }

                Ticket ticket = new Ticket();

                ticket.setBookingId(bookingId);

                // M3: resolve eventId from booking.placed mapping (Strategy A) — fall back to
                // the Feign response if the mapping hasn't been consumed yet
                ticket.setEventId(resolveEventId(bookingId, booking));

                ticket.setAttendeeName(attendeeName);

                ticket.setTicketCode(ticketCode);

                ticket.setMetadata(metadata);

                ticket.setIssuedAt(LocalDateTime.now());

                ticket.setStatus(TicketStatus.VALID);

                Ticket saved = ticketRepository.save(ticket);

                MDC.put("ticketId", saved.getId().toString());

                log.info(
                                "{} {} saved with status={}",
                                "Ticket",
                                saved.getId(),
                                saved.getStatus());

                cacheEviction.evictPattern("S4-F1");
                cacheEviction.evictPattern("S4-F9");
                cacheEviction.evictPattern("S4-F10");

                ticketEventPublisher.publishTicketIssued(
                                new TicketIssuedEvent(saved.getId(), bookingId, saved.getAttendeeName(),
                                                saved.getTicketCode()));

                notifyObservers(
                                "TICKET_ISSUED",
                                Map.of(
                                                "ticketId", saved.getId(),
                                                "bookingId", bookingId));

                MDC.remove("ticketId");
                MDC.remove("bookingId");
                MDC.remove("eventId");

                return saved;
        }

        @Cacheable(cacheNames = "ticket-service::S4-F3", key = "#lat + ':' + #lon + ':' + #radiusKm")
        public List<NearbyTicketDTO> findNearbyTickets(
                        double lat,
                        double lon,
                        double radiusKm) {

                long start = System.currentTimeMillis();

                List<Ticket> tickets = ticketRepository.findByStatus(TicketStatus.VALID);

                List<NearbyTicketDTO> results = new ArrayList<>();

                Map<Long, VenueCoordsDTO> venueCache = new HashMap<>();

                for (Ticket ticket : tickets) {

                        MDC.put("ticketId", ticket.getId().toString());
                        MDC.put("bookingId", ticket.getBookingId().toString());

                        try {

                                log.info(
                                                "Calling {}.{} with args={}",
                                                "BookingServiceClient",
                                                "getBooking",
                                                ticket.getBookingId());

                                BookingDTO booking = bookingServiceFacade.getBooking(ticket.getBookingId());

                                log.info(
                                                "{}.{} returned successfully",
                                                "BookingServiceClient",
                                                "getBooking");

                                Long eventId = booking.eventId();

                                MDC.put("eventId", eventId.toString());

                                log.info(
                                                "Calling {}.{} with args={}",
                                                "EventServiceClient",
                                                "getEvent",
                                                eventId);

                                EventDTO event = eventServiceClient.getEvent(eventId);

                                log.info(
                                                "{}.{} returned successfully",
                                                "EventServiceClient",
                                                "getEvent");

                                VenueCoordsDTO venue;

                                if (venueCache.containsKey(eventId)) {

                                        venue = venueCache.get(eventId);

                                } else {

                                        log.info(
                                                        "Calling {}.{} with args={}",
                                                        "EventServiceClient",
                                                        "getEventVenueCoords",
                                                        eventId);

                                        venue = eventServiceClient.getEventVenueCoords(eventId);

                                        log.info(
                                                        "{}.{} returned successfully",
                                                        "EventServiceClient",
                                                        "getEventVenueCoords");

                                        venueCache.put(eventId, venue);
                                }

                                double eventLat = venue.venueLat();
                                double eventLon = venue.venueLon();

                                double distanceKm = Math.sqrt(
                                                Math.pow(eventLat - lat, 2) +
                                                                Math.pow(eventLon - lon, 2))
                                                * 111;

                                if (distanceKm <= radiusKm) {

                                        NearbyTicketDTO dto = NearbyTicketDTO.builder()
                                                        .ticketId(ticket.getId())
                                                        .attendeeName(ticket.getAttendeeName())
                                                        .bookingId(ticket.getBookingId())
                                                        .eventName(event.name())
                                                        .eventLat(eventLat)
                                                        .eventLon(eventLon)
                                                        .distanceKm(distanceKm)
                                                        .build();

                                        results.add(dto);

                                        log.info(
                                                        "NEARBY_TICKET_FOUND ticketId={} bookingId={} eventId={}",
                                                        ticket.getId(),
                                                        ticket.getBookingId(),
                                                        eventId);
                                }

                        } catch (Exception e) {

                                log.warn(
                                                "Feign call to {} failed: {}",
                                                "booking-service",
                                                e.getMessage());

                        } finally {

                                MDC.remove("ticketId");
                                MDC.remove("bookingId");
                                MDC.remove("eventId");
                        }
                }

                results.sort((a, b) -> Double.compare(a.getDistanceKm(), b.getDistanceKm()));

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {

                        log.warn(
                                        "Slow {} took {}ms",
                                        "findNearbyTickets",
                                        elapsed);
                }

                return results;
        }

        // ─── [S4-F7] Purge Expired Tickets (write — invalidates) ─────────────────────
        @Transactional
        public int purgeExpiredTickets(int olderThanDays) {
                long start = System.currentTimeMillis();

                MDC.put("olderThanDays", String.valueOf(olderThanDays));

                LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
                int count = ticketRepository.purgeExpiredAndCancelledTickets(cutoff);

                log.info(
                                "TICKETS_PURGED olderThanDays={} cutoff={} purgedCount={}",
                                olderThanDays,
                                cutoff,
                                count);

                cacheEviction.evictPattern("S4-F1");
                cacheEviction.evictPattern("S4-F6");
                cacheEviction.evictPattern("S4-F9");
                cacheEviction.evictPattern("S4-F10");
                notifyObservers("TICKETS_PURGED", Map.of("olderThanDays", olderThanDays, "purgedCount", count));

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "purgeExpiredTickets",
                                        elapsed);
                }

                MDC.remove("olderThanDays");

                return count;
        }

        // ─── [S4-F8] Event Attendance Summary (relationship DTO) ─────────────────────

        // @Cacheable(cacheNames = "ticket-service::S4-F8", key = "#eventId")
        // public EventAttendanceSummaryDTO getSummary(Long eventId) {
        //
        // List<Ticket> tickets =
        // ticketRepository.findByEventId(eventId);
        //
        // if (tickets.isEmpty()) {
        // throw new ResponseStatusException(
        // HttpStatus.NOT_FOUND,
        // "No tickets found for event: " + eventId
        // );
        // }
        //
        // long totalTickets = tickets.size();
        //
        // long usedTickets = tickets.stream()
        // .filter(t -> t.getStatus() == TicketStatus.USED)
        // .count();
        //
        // long validTickets = tickets.stream()
        // .filter(t -> t.getStatus() == TicketStatus.VALID)
        // .count();
        //
        // double attendanceRate =
        // totalTickets == 0
        // ? 0.0
        // : ((double) usedTickets / totalTickets) * 100;
        //
        // LocalDateTime lastCheckIn = tickets.stream()
        // .filter(t -> t.getStatus() == TicketStatus.USED)
        // .map(Ticket::getIssuedAt)
        // .max(LocalDateTime::compareTo)
        // .orElse(null);
        //
        // return EventAttendanceSummaryDTO.builder()
        // .eventId(eventId)
        // .totalTickets(totalTickets)
        // .usedTickets(usedTickets)
        // .validTickets(validTickets)
        // .lastCheckIn(lastCheckIn)
        // .build();
        // }

        @Cacheable(cacheNames = "ticket-service::S4-F9", key = "'all'")
        public List<UnusedTicketDTO> getUnusedUpcomingTickets() {

                long start = System.currentTimeMillis();

                List<Ticket> tickets = ticketRepository.findByStatus(TicketStatus.VALID);

                List<UnusedTicketDTO> results = new ArrayList<>();

                Map<Long, EventDTO> eventCache = new HashMap<>();

                for (Ticket ticket : tickets) {

                        MDC.put("ticketId", ticket.getId().toString());
                        MDC.put("bookingId", ticket.getBookingId().toString());

                        try {

                                Long eventId = ticket.getEventId();

                                MDC.put("eventId", eventId.toString());

                                EventDTO event;

                                if (eventCache.containsKey(eventId)) {

                                        event = eventCache.get(eventId);

                                } else {

                                        log.info(
                                                        "Calling {}.{} with args={}",
                                                        "EventServiceClient",
                                                        "getEvent",
                                                        eventId);

                                        event = eventServiceClient.getEvent(eventId);

                                        log.info(
                                                        "{}.{} returned successfully",
                                                        "EventServiceClient",
                                                        "getEvent");

                                        eventCache.put(eventId, event);
                                }

                                if ("UPCOMING".equalsIgnoreCase(event.status())) {

                                        UnusedTicketDTO dto = UnusedTicketDTO.builder()
                                                        .ticketId(ticket.getId())
                                                        .attendeeName(ticket.getAttendeeName())
                                                        .ticketCode(ticket.getTicketCode())
                                                        .bookingId(ticket.getBookingId())
                                                        .eventName(event.name())
                                                        .eventDate(event.eventDate())
                                                        .build();

                                        results.add(dto);
                                }

                        } catch (Exception e) {

                                log.warn(
                                                "Feign call to {} failed: {}",
                                                "event-service",
                                                e.getMessage());

                        } finally {

                                MDC.remove("ticketId");
                                MDC.remove("bookingId");
                                MDC.remove("eventId");
                        }
                }

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {

                        log.warn(
                                        "Slow {} took {}ms",
                                        "getUnusedUpcomingTickets",
                                        elapsed);
                }

                return results;
        }

        // ─── [S4-F6] Ticket History (date range report)
        // ───────────────────────────────

        @Cacheable(cacheNames = "ticket-service::S4-F6", key = "T(String).valueOf(#startDate) + ':' + T(String).valueOf(#endDate) + ':' + T(String).valueOf(#status)")
        public List<Ticket> getTicketHistory(LocalDate startDate, LocalDate endDate, String status) {
                long queryStart = System.currentTimeMillis();

                MDC.put("startDate", String.valueOf(startDate));
                MDC.put("endDate", String.valueOf(endDate));

                if (status != null && !status.isBlank()) {
                        MDC.put("status", status);
                }

                if (startDate == null || endDate == null) {
                        log.warn(
                                        "INVALID_TICKET_HISTORY_REQUEST startDate={} endDate={}",
                                        startDate,
                                        endDate);

                        MDC.remove("startDate");
                        MDC.remove("endDate");
                        MDC.remove("status");

                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
                }
                if (endDate.isBefore(startDate)) {
                        log.warn(
                                        "INVALID_TICKET_HISTORY_RANGE startDate={} endDate={}",
                                        startDate,
                                        endDate);

                        MDC.remove("startDate");
                        MDC.remove("endDate");
                        MDC.remove("status");

                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "endDate must be on or after startDate");
                }

                LocalDateTime start = startDate.atStartOfDay();
                LocalDateTime end = endDate.atTime(LocalTime.MAX);

                List<Ticket> tickets;

                if (status == null || status.isBlank()) {
                        tickets = ticketRepository.findByIssuedAtBetween(start, end);
                } else {
                        String normalized = status.toUpperCase();
                        try {
                                TicketStatus.valueOf(normalized);
                        } catch (IllegalArgumentException e) {
                                log.warn(
                                                "INVALID_TICKET_HISTORY_STATUS status={}",
                                                status);

                                MDC.remove("startDate");
                                MDC.remove("endDate");
                                MDC.remove("status");

                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Invalid status: " + status
                                                                + ". Allowed: VALID, USED, EXPIRED, CANCELLED");
                        }

                        tickets = ticketRepository.findByIssuedAtBetweenAndStatus(start, end, normalized);
                }

                log.info(
                                "TICKET_HISTORY_FOUND startDate={} endDate={} status={} count={}",
                                startDate,
                                endDate,
                                status,
                                tickets.size());

                long elapsed = System.currentTimeMillis() - queryStart;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "getTicketHistory",
                                        elapsed);
                }

                MDC.remove("startDate");
                MDC.remove("endDate");
                MDC.remove("status");

                return tickets;
        }

        // ─── [S4-F5] Filter Tickets by Metadata (JSONB)
        // ───────────────────────────────

        @Cacheable(cacheNames = "ticket-service::S4-F5", key = "#key + ':' + #operator + ':' + #value")
        public List<Ticket> filterByMetadata(String key, String operator, String value) {
                long start = System.currentTimeMillis();

                MDC.put("metadataKey", String.valueOf(key));
                MDC.put("metadataOperator", String.valueOf(operator));

                if (key == null || key.isBlank() || operator == null || value == null) {
                        log.warn(
                                        "INVALID_METADATA_FILTER key={} operator={} valuePresent={}",
                                        key,
                                        operator,
                                        value != null);

                        MDC.remove("metadataKey");
                        MDC.remove("metadataOperator");

                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "key, operator, and value are required");
                }

                List<Ticket> tickets = switch (operator) {
                        case "eq" -> ticketRepository.findByMetadataEquals(key, value);
                        case "gt" -> ticketRepository.findByMetadataGreaterThan(key, value);
                        case "lt" -> ticketRepository.findByMetadataLessThan(key, value);
                        default -> {
                                log.warn(
                                                "INVALID_METADATA_OPERATOR operator={}",
                                                operator);

                                MDC.remove("metadataKey");
                                MDC.remove("metadataOperator");

                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Invalid operator: " + operator + ". Allowed: eq, gt, lt");
                        }
                };

                log.info(
                                "METADATA_FILTER_APPLIED key={} operator={} count={}",
                                key,
                                operator,
                                tickets.size());

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "filterByMetadata",
                                        elapsed);
                }

                MDC.remove("metadataKey");
                MDC.remove("metadataOperator");

                return tickets;
        }

        // ─── [S4-F10] Ticket Analytics (aggregated report)
        // Caches the result in Redis. The key combines startDate and endDate.
        // Note: TTL configuration (10 mins) is typically handled in your
        // RedisCacheManager config.
        @Cacheable(value = "ticket-service::S4-F10", key = "#startDate.toString() + '_' + #endDate.toString()")
        public TicketAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
                long start = System.currentTimeMillis();

                MDC.put("startDate", String.valueOf(startDate));
                MDC.put("endDate", String.valueOf(endDate));

                // 1. Validate date range - throws 400 if startDate is after endDate[cite: 1]
                if (startDate.isAfter(endDate)) {
                        log.warn(
                                        "INVALID_ANALYTICS_RANGE startDate={} endDate={}",
                                        startDate,
                                        endDate);

                        MDC.remove("startDate");
                        MDC.remove("endDate");

                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
                }

                // 2. Expand window to fully-closed range [startDateT00:00:00,
                // endDateT23:59:59.999][cite: 1]
                LocalDateTime startDateTime = startDate.atStartOfDay();
                LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

                // 3. Fetch data
                List<Ticket> tickets = ticketRepository.findAllByIssuedAtBetween(startDateTime, endDateTime);

                // 4. Aggregate ticket counts by status[cite: 1]
                Map<String, Long> ticketsByStatus = tickets.stream()
                                .collect(Collectors.groupingBy(
                                                ticket -> ticket.getStatus().name(),
                                                Collectors.counting()));

                // Extract specific counts (default to 0 if not present)[cite: 1]
                long usedCount = ticketsByStatus.getOrDefault("USED", 0L);
                long validCount = ticketsByStatus.getOrDefault("VALID", 0L);
                long expiredCount = ticketsByStatus.getOrDefault("EXPIRED", 0L);
                long cancelledCount = ticketsByStatus.getOrDefault("CANCELLED", 0L);

                // 5. Calculate totals and attendance rate[cite: 1]
                long totalIssued = tickets.size();
                double attendanceRate = totalIssued == 0 ? 0.0 : (double) usedCount / totalIssued;

                log.info(
                                "TICKET_ANALYTICS_GENERATED startDate={} endDate={} totalIssued={} usedCount={} validCount={} expiredCount={} cancelledCount={}",
                                startDate,
                                endDate,
                                totalIssued,
                                usedCount,
                                validCount,
                                expiredCount,
                                cancelledCount);

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {
                        log.warn(
                                        "Slow {} took {}ms",
                                        "getAnalytics",
                                        elapsed);
                }

                MDC.remove("startDate");
                MDC.remove("endDate");

                // 6. Build and return DTO using the Builder Pattern[cite: 1]
                return TicketAnalyticsDTO.builder()
                                .totalIssued(totalIssued)
                                .usedCount(usedCount)
                                .validCount(validCount)
                                .expiredCount(expiredCount)
                                .cancelledCount(cancelledCount)
                                .attendanceRate(attendanceRate)
                                .ticketsByStatus(ticketsByStatus)
                                .build();
        }

        // new first endpoint
        public int getUsedTicketCountForBooking(Long bookingId) {

                MDC.put("bookingId", bookingId.toString());

                List<Ticket> tickets = ticketRepository.findByBookingIdAndStatus(
                                bookingId,
                                TicketStatus.USED);

                log.info(
                                "USED_TICKET_COUNT bookingId={} count={}",
                                bookingId,
                                tickets.size());

                MDC.remove("bookingId");

                return tickets.size();
        }

        // new second endpoint
        @Cacheable(cacheNames = "ticket-service::S4-F8", key = "#eventId")
        public EventTicketSummaryDTO getEventTicketSummary(Long eventId) {

                long start = System.currentTimeMillis();

                MDC.put("eventId", eventId.toString());

                List<Ticket> tickets = ticketRepository.findByEventId(eventId);

                if (tickets.isEmpty()) {

                        MDC.remove("eventId");

                        throw new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "No tickets found for event: " + eventId);
                }

                long totalTicketsSold = tickets.size();

                long usedCount = tickets.stream()
                                .filter(t -> t.getStatus() == TicketStatus.USED)
                                .count();

                log.info(
                                "EVENT_TICKET_SUMMARY eventId={} totalTicketsSold={} usedCount={}",
                                eventId,
                                totalTicketsSold,
                                usedCount);

                long elapsed = System.currentTimeMillis() - start;

                if (elapsed > 1000) {

                        log.warn(
                                        "Slow {} took {}ms",
                                        "getEventTicketSummary",
                                        elapsed);
                }

                MDC.remove("eventId");

                return new EventTicketSummaryDTO(
                                totalTicketsSold,
                                usedCount);
        }
}
