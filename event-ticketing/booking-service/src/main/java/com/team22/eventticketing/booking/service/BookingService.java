package com.team22.eventticketing.booking.service;

import com.team22.eventticketing.booking.adapter.AnalyticsDashboardTarget;
import com.team22.eventticketing.booking.adapter.Neo4jRecordAdapter;
import com.team22.eventticketing.booking.dto.*;
import com.team22.eventticketing.booking.dto.BookingItemDTO;
import com.team22.eventticketing.booking.model.*;
import com.team22.eventticketing.booking.observer.EntityObserver;
import com.team22.eventticketing.booking.repository.BookingItemRepository;
import com.team22.eventticketing.booking.repository.BookingRepository;
import com.team22.eventticketing.booking.repository.EventNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeRepository;
import com.team22.eventticketing.booking.repository.UserNodeCustomRepository;
import com.team22.eventticketing.booking.repository.UserNodeRecommendationRepository;
import com.team22.eventticketing.booking.security.AuthContext;
import com.team22.eventticketing.contracts.dto.*;
import com.team22.eventticketing.contracts.events.BookingCancelledEvent;
import com.team22.eventticketing.contracts.events.BookingCompletedEvent;
import com.team22.eventticketing.contracts.events.BookingPlacedEvent;
import com.team22.eventticketing.contracts.feign.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.server.ResponseStatusException;
import com.team22.eventticketing.booking.messaging.BookingEventPublisher;

import java.math.BigDecimal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
public class BookingService {

    private static final String ESTIMATE_CACHE_PREFIX = "booking-service::S3-F3::";
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final CacheEvictionService cacheEviction;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsDashboardTarget dashboardAdapter;
    private final List<EntityObserver> observers = new ArrayList<>();

    // Neo4j Repositories
    private final UserNodeRepository userNodeRepository;
    private final EventNodeRepository eventNodeRepository;
    private final UserNodeCustomRepository userNodeCustomRepository; // FIXED: Added missing field
    private final UserNodeRecommendationRepository userNodeRecommendationRepository; // FIXED: Added missing field
    // wiring the feign calls
    private final EventServiceClient eventServiceClient;
    private final UserServiceClient userServiceClient;
    private final TicketServiceClient ticketServiceClient;
    private final BookingEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          AnalyticsDashboardTarget dashboardAdapter,
                          BookingItemRepository bookingItemRepository,
                          CacheEvictionService cacheEviction,
                          RedisTemplate<String, Object> redisTemplate,
                          EventNodeRepository eventNodeRepository,
                          UserNodeRepository userNodeRepository,
                          List<EntityObserver> observers,
                          UserNodeCustomRepository userNodeCustomRepository,
                          UserNodeRecommendationRepository userNodeRecommendationRepository,
                          EventServiceClient eventServiceClient,
                          UserServiceClient userServiceClient,
                          TicketServiceClient ticketServiceClient,
                          BookingEventPublisher eventPublisher

    ) {

        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.cacheEviction = cacheEviction;
        this.redisTemplate = redisTemplate;
        this.eventNodeRepository = eventNodeRepository;
        this.userNodeRepository = userNodeRepository;
        this.dashboardAdapter = dashboardAdapter;
        this.observers.addAll(observers);
        this.userNodeCustomRepository = userNodeCustomRepository;
        this.userNodeRecommendationRepository = userNodeRecommendationRepository;
        this.eventServiceClient=eventServiceClient;
        this.userServiceClient=userServiceClient;
        this.ticketServiceClient=ticketServiceClient;
        this.eventPublisher = eventPublisher;
    }

// we have the following methods currently querying other services dbs
    //1-confirmBooking() : calls findEventStatusById in the repo
    //2-estimateCost() : calls getAverageSessionCapacity in the booking repo
    //3-recordAttendance() : calls findUserNameById and findEventDetailsById in the booking repo
    //4-getRecommendations() : calls findeventsbyid in the repo

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }

    // ─── [S3-F1] Search Bookings ──────────────────────────────────────────────────

    @Cacheable(
            cacheNames = "booking-service::S3-F1",
            key = "(#status != null ? #status.name() : 'ALL') + ':' + #startDate.toString() + ':' + #endDate.toString()"
    )
    public List<Booking> searchBookings(BookingStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        if (status != null) {
            return bookingRepository.findByStatusAndBookingDateBetweenOrderByBookingDateDesc(status, startDate, endDate);
        } else {
            return bookingRepository.findByBookingDateBetweenOrderByBookingDateDesc(startDate, endDate);
        }
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    public Booking create(Booking booking) {
        if (booking.getBookingDate() == null) {
            booking.setBookingDate(LocalDateTime.now());
        }
        Booking saved = bookingRepository.save(booking);
        // ── DB write success ──
        MDC.put("bookingId", String.valueOf(saved.getId()));
        try {
            log.info("Booking {} saved with status={}", saved.getId(), saved.getStatus());
        } finally {
            MDC.remove("bookingId");
        }
        notifyObservers("BOOKING_CREATED", Map.of("bookingId", saved.getId()));
        BookingPlacedEvent event = new BookingPlacedEvent(saved.getId(), saved.getUserId(), saved.getEventId());
        eventPublisher.publishBookingPlaced(event);
        return saved;
    }

    // ─── [CRUD] Read by ID ────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "booking-service::booking", key = "#id")
    public Booking findById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with id: " + id));
    }

    // ─── Read all (NOT cached) ────────────────────────────────────────────────────

    public List<Booking> findAll() {
        return bookingRepository.findAll();
    }

    public List<Booking> findByUserId(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    // ─── [CRUD] Update ────────────────────────────────────────────────────────────

    public Booking update(Long id, Booking patch) {
        Booking existing = findById(id);

        if (patch.getContactEmail() != null) existing.setContactEmail(patch.getContactEmail());
        if (patch.getStatus() != null)       existing.setStatus(patch.getStatus());
        if (patch.getTotalAmount() != null)  existing.setTotalAmount(patch.getTotalAmount());
        if (patch.getMetadata() != null)     existing.setMetadata(patch.getMetadata());
        if (patch.getConfirmedAt() != null)  existing.setConfirmedAt(patch.getConfirmedAt());
        if (patch.getEventId() != null)      existing.setEventId(patch.getEventId());

        Booking saved = bookingRepository.save(existing);
        // ── DB write success ──
        MDC.put("bookingId", String.valueOf(saved.getId()));
        try {
            log.info("Booking {} saved with status={}", saved.getId(), saved.getStatus());
        } finally {
            MDC.remove("bookingId");
        }
        cacheEviction.evictEntityAndFeatures("booking", id, "S3-F1", "S3-F5", "S3-F6", "S3-F9", "S3-F10");
        notifyObservers("BOOKING_UPDATED", Map.of("bookingId", saved.getId()));
        return saved;
    }

    // ─── [CRUD] Delete ────────────────────────────────────────────────────────────

    public void delete(Long id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found with id: " + id);
        }
        bookingRepository.deleteById(id);
        cacheEviction.evictEntityAndFeatures("booking", id, "S3-F1", "S3-F5", "S3-F6", "S3-F9", "S3-F10");
        notifyObservers("BOOKING_DELETED", Map.of("bookingId", id));
    }

    // ─── [S3-F4] Complete Booking (write — invalidates) ──────────────────────────

    @Transactional
    public Booking completeBooking(Long id) {
        MDC.put("bookingId", String.valueOf(id));
        try {
            Booking booking = bookingRepository.findByIdWithItems(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Booking not found with id: " + id));

            if (booking.getStatus() != BookingStatus.CHECKED_IN) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only CHECKED_IN bookings can be completed");
            }

            BookingStatus oldStatus = booking.getStatus();   // capture before transition
            booking.setStatus(BookingStatus.COMPLETED);

            // ── Saga state transition ──
            log.info("Booking {} transitioning {} → {}", id, oldStatus, BookingStatus.COMPLETED);

            if (booking.getTotalAmount() == null && booking.getBookingItems() != null) {
                double total = booking.getBookingItems().stream()
                        .mapToDouble(item -> item.getQuantity() * item.getUnitPrice())
                        .sum();
                booking.setTotalAmount(total);
            }

            Booking saved = bookingRepository.save(booking);

            // ── DB write success ──
            MDC.put("userId",  String.valueOf(saved.getUserId()));
            MDC.put("eventId", String.valueOf(saved.getEventId()));
            log.info("Booking {} saved with status={}", saved.getId(), saved.getStatus());

            bookingRepository.insertTicketSale(
                    saved.getId(),
                    saved.getUserId(),
                    saved.getTotalAmount() != null ? saved.getTotalAmount() : 0.0
            );

            cacheEviction.evictEntityAndFeatures("booking", id, "S3-F1", "S3-F6", "S3-F9", "S3-F10");
            notifyObservers("BOOKING_COMPLETED", Map.of("bookingId", saved.getId()));
            BigDecimal amount = saved.getTotalAmount() != null ? BigDecimal.valueOf(saved.getTotalAmount()) : BigDecimal.ZERO;
            BookingCompletedEvent event = new BookingCompletedEvent(saved.getId(), saved.getUserId(), saved.getEventId(), amount);
            eventPublisher.publishBookingCompleted(event);
            return saved;
        } finally {
            MDC.remove("bookingId");
            MDC.remove("userId");
            MDC.remove("eventId");
        }
    }

    // ─── [S3-F5] Filter Bookings by Metadata (JSONB) ─────────────────────────────

    @Cacheable(cacheNames = "booking-service::S3-F5", key = "#key + ':' + #value")
    public List<Booking> filterByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Key and value must not be blank");
        }
        return bookingRepository.findByMetadataField(key, value);
    }

    // ─── [S3-F6] Booking Analytics (report) ──────────────────────────────────────

    @Cacheable(cacheNames = "booking-service::S3-F6",
            key = "T(String).valueOf(#start) + ':' + T(String).valueOf(#end)")
    public BookingAnalyticsDTO getAnalytics(LocalDateTime start, LocalDateTime end) {
        long total     = bookingRepository.countInRange(start, end);
        long completed = bookingRepository.countCompletedInRange(start, end);
        long cancelled = bookingRepository.countCancelledInRange(start, end);
        double revenue = bookingRepository.sumRevenueInRange(start, end);
        double avg     = completed > 0 ? revenue / completed : 0.0;
        double rate    = total > 0 ? (completed * 100.0) / total : 0.0;

        return BookingAnalyticsDTO.builder()
                .totalBookings(total)
                .completedBookings(completed)
                .cancelledBookings(cancelled)
                .totalRevenue(revenue)
                .averageBookingAmount(avg)
                .completionRate(rate)
                .build();
    }

    // ─── [S3-F2] Cancel Booking (write — invalidates) ────────────────────────────

    @Transactional
    public void cancelBooking(Long id) {
        MDC.put("bookingId", String.valueOf(id));
        try {
            Booking booking = bookingRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Booking not found with id: " + id));

            if (booking.getStatus() != BookingStatus.PENDING &&
                    booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only PENDING or CONFIRMED bookings can be cancelled");
            }

            BookingStatus oldStatus = booking.getStatus();   // capture before transition
            booking.setStatus(BookingStatus.CANCELLED);

            // ── Saga state transition ──
            log.info("Booking {} transitioning {} → {}", id, oldStatus, BookingStatus.CANCELLED);

            bookingRepository.save(booking);

            // ── DB write success ──
            log.info("Booking {} saved with status={}", id, BookingStatus.CANCELLED);

            bookingRepository.cancelValidTicketsForBooking(id);
            cacheEviction.evictEntityAndFeatures("booking", id, "S3-F1", "S3-F6", "S3-F9", "S3-F10");
            notifyObservers("BOOKING_CANCELLED", Map.of("bookingId", id));
            BookingCancelledEvent event = new BookingCancelledEvent(id, booking.getUserId(), booking.getEventId(), "User requested cancellation");
            eventPublisher.publishBookingCancelled(event);
        } finally {
            MDC.remove("bookingId");
        }
    }

    // ─── [S3-F8] Add Items to Booking (write — invalidates) ──────────────────────

    @Transactional
    public Booking addItemsToBooking(Long bookingId, List<BookingItemRequest> requests) {
        MDC.put("bookingId", String.valueOf(bookingId));
        try {
            Booking booking = bookingRepository.findByIdWithItems(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Booking not found with id: " + bookingId));

            if (booking.getStatus() != BookingStatus.PENDING &&
                    booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot add items to a booking that is not PENDING or CONFIRMED");
            }

            for (BookingItemRequest req : requests) {
                if (req.getSessionId() == null || req.getSessionTitle() == null ||
                        req.getSessionTitle().isBlank() || req.getQuantity() == null ||
                        req.getUnitPrice() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Each item must have sessionId, sessionTitle, quantity, and unitPrice");
                }
            }

            int currentMax = bookingRepository.findMaxEventOrderByBookingId(bookingId);

            for (BookingItemRequest req : requests) {
                currentMax++;
                BookingItem item = new BookingItem();
                item.setEventOrder(currentMax);
                item.setSessionId(req.getSessionId());
                item.setSessionTitle(req.getSessionTitle());
                item.setQuantity(req.getQuantity());
                item.setUnitPrice(req.getUnitPrice());
                item.setStatus(BookingItemStatus.RESERVED);
                item.setMetadata(req.getMetadata());
                item.setBooking(booking);
                bookingItemRepository.save(item);
            }

            Booking result = bookingRepository.findByIdWithItems(bookingId).get();

            // ── DB write success ──
            log.info("Booking {} saved with status={}", bookingId, result.getStatus());

            cacheEviction.evictEntityAndFeatures("booking", bookingId, "S3-F1", "S3-F6", "S3-F9", "S3-F10");
            notifyObservers("BOOKING_ITEMS_ADDED", Map.of("bookingId", bookingId));
            return result;
        } finally {
            MDC.remove("bookingId");
        }
    }

    // ─── [S3-F9] Booking Details DTO ─────────────────────────────────────────────

    @Cacheable(cacheNames = "booking-service::S3-F9", key = "#bookingId")
    public BookingDetailsDTO getBookingDetails(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithItems(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found with id: " + bookingId));

        List<BookingItemDTO> itemDTOs = booking.getBookingItems() == null
                ? List.of()
                : booking.getBookingItems().stream()
                .sorted((a, b) -> Integer.compare(a.getEventOrder(), b.getEventOrder()))
                .map(item -> new BookingItemDTO(
                        item.getId(),
                        item.getEventOrder(),
                        item.getSessionTitle(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getStatus().name(),
                        item.getMetadata()
                ))
                .collect(Collectors.toList());

        long confirmedItems = itemDTOs.stream()
                .filter(i -> i.getStatus().equals("CONFIRMED"))
                .count();

        return BookingDetailsDTO.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .eventId(booking.getEventId())
                .status(booking.getStatus().name())
                .totalAmount(booking.getTotalAmount())
                .metadata(booking.getMetadata())
                .items(itemDTOs)
                .totalItems(itemDTOs.size())
                .confirmedItems(confirmedItems)
                .build();
    }

    // ─── [S3-F7] Confirm Booking (write — invalidates) ───────────────────────────

    @Transactional
    public Booking confirmBooking(Long bookingId, Long eventId) {
        MDC.put("bookingId", String.valueOf(bookingId));
        MDC.put("eventId",   String.valueOf(eventId));
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

            // ── Feign: before request ──
            log.info("Calling {}.{} with args={}", "EventServiceClient", "getEvent", eventId);

            //String eventStatus = bookingRepository.findEventStatusById(eventId); using a method that queries another table
            EventDTO event;
            try {
                event = eventServiceClient.getEvent(eventId);
                // ── Feign: after success ──
                log.info("{}.{} returned successfully", "EventServiceClient", "getEvent");
            } catch (feign.FeignException.NotFound e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
            } catch (feign.FeignException e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Event service unavailable"
                );
            }

            // if (eventStatus == null) {
            //   throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event with ID " + eventId + "Event not found");
            //}

            if (booking.getStatus() != BookingStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking is not PENDING. Current status: " + booking.getStatus());
            }

            if (!"UPCOMING".equals(event.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is not UPCOMING. Current status: " + event.status());
            }

            BookingStatus oldStatus = booking.getStatus();   // capture before transition
            booking.setEventId(eventId);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setConfirmedAt(LocalDateTime.now());

            // ── Saga state transition ──
            log.info("Booking {} transitioning {} → {}", bookingId, oldStatus, BookingStatus.CONFIRMED);

            Booking saved = bookingRepository.save(booking);

            // ── DB write success ──
            log.info("Booking {} saved with status={}", saved.getId(), saved.getStatus());

            cacheEviction.evictEntityAndFeatures("booking", bookingId, "S3-F1", "S3-F9", "S3-F10");
            return saved;
        } finally {
            MDC.remove("bookingId");
            MDC.remove("eventId");
        }
    }

    // ─── [S3-F3] Booking Cost Estimate (POST — manual cache by body hash) ─────────

    public BookingCostEstimateDTO estimateCost(Long eventId, Integer ticketCount, String ticketTier) {
        MDC.put("eventId", String.valueOf(eventId));
        try {
            String cacheKey = ESTIMATE_CACHE_PREFIX + DigestUtils.sha256Hex(eventId + ":" + ticketCount + ":" + ticketTier);

            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached instanceof BookingCostEstimateDTO dto) return dto;
            } catch (Exception ignored) {}

            // Double avgCapacity = bookingRepository.getAverageSessionCapacity(eventId); A line again invoking
            // if (avgCapacity == null) avgCapacity = 0.0;
            Double avgCapacity = 0.0;

            // ── Feign: before request ──
            log.info("Calling {}.{} with args={}", "EventServiceClient", "getEventAvgCapacity", eventId);
            try {
                AvgCapacityDTO capacityDTO = eventServiceClient.getEventAvgCapacity(eventId);
                // ── Feign: after success ──
                log.info("{}.{} returned successfully", "EventServiceClient", "getEventAvgCapacity");
                if (capacityDTO != null && capacityDTO.avgCapacity() != null) {
                    avgCapacity = capacityDTO.avgCapacity();
                }
            } catch (feign.FeignException.NotFound e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
            } catch (feign.FeignException e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service unavailable");
            }

            double basePrice = avgCapacity / 10.0;
            double tierMultiplier = "VIP".equalsIgnoreCase(ticketTier) ? 2.5 : 1.0;
            double ticketCost = basePrice * tierMultiplier * ticketCount;
            double serviceFee = ticketCost * 0.15;

            Long activeBookings = bookingRepository.countActiveBookingsForEvent(eventId);
            double demandMultiplier = 1.0;
            if (activeBookings != null) {
                if (activeBookings > 200)     demandMultiplier = 1.5;
                else if (activeBookings > 50) demandMultiplier = 1.25;
            }

            double estimatedTotal = (ticketCost + serviceFee) * demandMultiplier;
            BookingCostEstimateDTO result = BookingCostEstimateDTO.builder()
                    .ticketCost(ticketCost)
                    .serviceFee(serviceFee)
                    .estimatedTotal(estimatedTotal)
                    .demandMultiplier(demandMultiplier)
                    .build();

            try {
                redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(5));
            } catch (Exception ignored) {}

            return result;
        } finally {
            MDC.remove("eventId");
        }
    }

    // ─── Milestone 2: Record Attendance ───────────────────────────────────────────

    public void recordAttendance(Long bookingId) {
        MDC.put("bookingId", String.valueOf(bookingId));
        try {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Booking not found with id: " + bookingId
                    ));

            if (booking.getStatus() != BookingStatus.COMPLETED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Booking must be COMPLETED"
                );
            }

            if (booking.getEventId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Booking has no assigned event"
                );
            }

            Long userId = booking.getUserId();
            Long eventId = booking.getEventId();
            MDC.put("userId",  String.valueOf(userId));
            MDC.put("eventId", String.valueOf(eventId));

            //   String userName = bookingRepository.findUserNameById(userId);
            // if (userName == null || userName.isBlank()) {
            //   userName = "Unknown";
            //}


            //   List<Object[]> eventDetails = bookingRepository.findEventDetailsById(eventId);
            // String eventName = "Unknown";
            //String eventCategory = "UNKNOWN";

            //if (eventDetails != null && !eventDetails.isEmpty()) {
            //  Object[] row = eventDetails.get(0);
            // if (row.length > 0 && row[0] != null) {
            //   eventName = row[0].toString();
            //}
            //if (row.length > 1 && row[1] != null) {
            //  eventCategory = row[1].toString();
            //}
            //}

            String userName = "Unknown";
            // ── Feign: before request ──
            log.info("Calling {}.{} with args={}", "UserServiceClient", "getUser", userId);
            try {
                UserDTO user = userServiceClient.getUser(userId);
                // ── Feign: after success ──
                log.info("{}.{} returned successfully", "UserServiceClient", "getUser");
                if (user != null && user.email() != null) {
                    userName = user.email();
                }
            } catch (feign.FeignException.NotFound e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "user-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            } catch (feign.FeignException e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "user-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
            }

            String eventName = "Unknown";
            String eventCategory = "UNKNOWN";
            // ── Feign: before request ──
            log.info("Calling {}.{} with args={}", "EventServiceClient", "getEvent", eventId);
            try {
                EventDTO event = eventServiceClient.getEvent(eventId);
                // ── Feign: after success ──
                log.info("{}.{} returned successfully", "EventServiceClient", "getEvent");
                if (event != null) {
                    eventName = event.name() != null ? event.name() : "Unknown";
                }
            } catch (feign.FeignException.NotFound e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
            } catch (feign.FeignException e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event service unavailable");
            }

            if (userNodeCustomRepository.countRecorded(userId, eventId, bookingId) > 0) {
                return; // idempotent no-op
            }

            userNodeCustomRepository.recordAttendance(
                    userId, userName,
                    eventId, eventName, eventCategory,
                    bookingId
            );

            // ── DB write success (Neo4j attendance node) ──
            log.info("Attendance {} saved with status={}", bookingId, "RECORDED");

            notifyObservers(
                    "INTERACTION_RECORDED",
                    Map.of(
                            "bookingId", bookingId,
                            "userId", userId,
                            "eventId", eventId
                    )
            );
        } finally {
            MDC.remove("bookingId");
            MDC.remove("userId");
            MDC.remove("eventId");
        }
    }

    // ─── Milestone 2: Recommendations ─────────────────────────────────────────────

    public List<EventRecommendationDTO> getRecommendations(Long userId, Integer limit, AuthContext ctx) {
        MDC.put("userId", String.valueOf(userId));
        try {
            Long callerId = ctx.getUserId();
            String role = ctx.getUserRole();
            if (!callerId.equals(userId) && !"ADMIN".equals(role)) {
                throw new AccessDeniedException("Forbidden");
            }

            /*if (!bookingRepository.existsUserById(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }*/

            // ── Feign: before request ──
            log.info("Calling {}.{} with args={}", "UserServiceClient", "getUser", userId);
            try {
                userServiceClient.getUser(userId);
                // ── Feign: after success ──
                log.info("{}.{} returned successfully", "UserServiceClient", "getUser");
            } catch (feign.FeignException.NotFound e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "user-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            } catch (feign.FeignException e) {
                // ── Feign: exception caught ──
                log.warn("Feign call to {} failed: {}", "user-service", e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
            }

            int effectiveLimit = (limit == null) ? 5 : limit;

            String cacheKey = "booking-service::S3-F12::" + userId + "::" + effectiveLimit;
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached instanceof List<?> list) {
                    return (List<EventRecommendationDTO>) list;
                }
            } catch (Exception ignored) {}

            List<org.neo4j.driver.Record> records = userNodeRecommendationRepository.findRecommendations(userId, effectiveLimit);

            Neo4jRecordAdapter adapter = new Neo4jRecordAdapter();
            List<EventRecommendationDTO> results = new ArrayList<>();

            for (org.neo4j.driver.Record record : records) {
                EventRecommendationDTO dto = adapter.adapt(record);
                MDC.put("eventId", String.valueOf(dto.getEventId()));
                try {
                  /*  List<Object[]> rows = bookingRepository.findEventDetailsById(dto.getEventId());
                    if (rows != null && !rows.isEmpty()) {
                        Object[] row = rows.get(0);
                        dto.setName(row[0] != null ? (String) row[0] : null);
                        dto.setCategory(row[1] != null ? (String) row[1] : null);
                        if (row[2] instanceof java.sql.Timestamp ts) {
                            dto.setEventDate(ts.toLocalDateTime());
                        } else if (row[2] instanceof java.sql.Date d) {
                            dto.setEventDate(d.toLocalDate().atStartOfDay());
                        }
                    }
                 */
                    // ── Feign: before request ──
                    log.info("Calling {}.{} with args={}", "EventServiceClient", "getEvent", dto.getEventId());
                    try {
                        EventDTO event = eventServiceClient.getEvent(dto.getEventId());
                        // ── Feign: after success ──
                        log.info("{}.{} returned successfully", "EventServiceClient", "getEvent");
                        if (event != null) {
                            dto.setName(event.name());
                            dto.setCategory(null); // EventDTO in contracts has no category field
                            dto.setEventDate(event.eventDate());
                        }
                    } catch (feign.FeignException e) {
                        // ── Feign: exception caught ──
                        log.warn("Feign call to {} failed: {}", "event-service", e.getMessage());
                        // skip enrichment if event service unavailable
                    }
                    results.add(dto);
                } finally {
                    MDC.remove("eventId");
                }
            }

            try {
                redisTemplate.opsForValue().set(cacheKey, results, Duration.ofMinutes(5));
            } catch (Exception ignored) {}

            return results;
        } finally {
            MDC.remove("userId");
        }
    }

    // ─── Milestone 2: Analytics Dashboard (S3-F10) ───────────────────────────────

    @Cacheable(
            cacheNames = "booking-service::S3-F10",
            key = "#startDate.toString() + ':' + #endDate.toString()"
    )
    public BookingAnalyticsDashboardDTO getDashboard(LocalDateTime startDate, LocalDateTime endDate) {

        StopWatch sw = new StopWatch();
        sw.start();

        // 1. Fetch raw data from PostgreSQL
        List<Object[]> results = bookingRepository.getDashboardAggregation(startDate, endDate);

        // 2. Observer Pattern: Log the view event to MongoDB
        notifyObservers("ANALYTICS_VIEWED", Map.of("startDate", startDate, "endDate", endDate));

        // 3. Adapter Pattern: Map the SQL rows to Java types & return directly
        BookingAnalyticsDashboardDTO dto = dashboardAdapter.adaptRawData(results);

        sw.stop();
        long elapsed = sw.getTotalTimeMillis();
        // ── Slow operation ──
        if (elapsed > 1000) {
            log.warn("Slow {} took {}ms", "getDashboard(S3-F10)", elapsed);
        }

        return dto;
    }



    // new methods for milestone 3 the action listeners for the new endpoints, what is going to be called when other services deal with ours
    public BookingSummaryDTO getUserBookingSummary(Long userId) {
        Long total = bookingRepository.countByUserId(userId);
        Long completed = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.COMPLETED);
        Long cancelled = bookingRepository.countByUserIdAndStatus(userId, BookingStatus.CANCELLED);
        BigDecimal totalSpent = bookingRepository.sumTotalAmountByUserIdAndStatus(userId, BookingStatus.COMPLETED);
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;
        BigDecimal avg = completed > 0
                ? totalSpent.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new BookingSummaryDTO(total, completed, cancelled, totalSpent, avg);
    }

    public Integer getUserActiveBookingCount(Long userId) {
        return bookingRepository.countActiveBookingsByUserId(userId);
    }

    public Long getUserBookingCount(Long userId, String status) {
        if (status != null && !status.isBlank()) {
            return bookingRepository.countByUserIdAndStatus(userId, BookingStatus.valueOf(status));
        }
        return bookingRepository.countByUserId(userId);
    }

    public BigDecimal getUserBookingTotal(Long userId, String startDate, String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        BigDecimal total = bookingRepository.sumCompletedAmountByUserIdInRange(userId, start, end);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Integer getEventActiveBookingCount(Long eventId) {
        return bookingRepository.countActiveBookingsByEventId(eventId);
    }

    public EventBookingRevenueDTO getEventRevenue(Long eventId, String startDate, String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        Long total = bookingRepository.countCompletedByEventIdInRange(eventId, start, end);
        BigDecimal revenue = bookingRepository.sumCompletedAmountByEventIdInRange(eventId, start, end);
        if (total == null) total = 0L;
        if (revenue == null) revenue = BigDecimal.ZERO;
        BigDecimal avg = total > 0
                ? revenue.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new EventBookingRevenueDTO(total, revenue, avg);
    }

    public List<BookingItemDTO> getBookingItemDTOs(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithItems(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (booking.getBookingItems() == null) return List.of();
        return booking.getBookingItems().stream()
                .map(item -> new BookingItemDTO(
                        item.getId(), item.getEventOrder(), item.getSessionTitle(),
                        item.getQuantity(), item.getUnitPrice(), item.getStatus().name(), item.getMetadata()
                ))
                .collect(Collectors.toList());
    }
}