package com.team22.eventticketing.ticket.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import com.team22.eventticketing.ticket.adapter.CassandraRowAdapter;
import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketScanEvent;
import com.team22.eventticketing.ticket.model.TicketScanEventKey;
import com.team22.eventticketing.ticket.model.dto.TicketScanDTO;
import com.team22.eventticketing.ticket.model.dto.TicketScanRequestDTO;
import com.team22.eventticketing.ticket.observer.EntityObserver;
import com.team22.eventticketing.ticket.observer.MongoEventLogger;
import com.team22.eventticketing.ticket.repository.TicketRepository;
import com.team22.eventticketing.ticket.repository.TicketScanEventRepository;
import io.jsonwebtoken.Claims;

@Service
public class TicketScanService {
    private final TicketRepository ticketRepository;
    private final TicketScanEventRepository cassandraRepository;
    private final JwtService jwtService;
    private MongoEventLogger mongoEventLogger;
    private final CacheEvictionService cacheEviction;
    private static final Logger log = LoggerFactory.getLogger(TicketScanService.class);


    public TicketScanService(TicketRepository ticketRepository,
                             TicketScanEventRepository cassandraRepo,
                             JwtService jwtService,
                             MongoEventLogger mongoEventLogger, CacheEvictionService cacheEviction) {
        this.ticketRepository = ticketRepository;
        this.cassandraRepository = cassandraRepo;
        this.jwtService = jwtService;
        this.mongoEventLogger = mongoEventLogger;
        this.cacheEviction = cacheEviction;
    }

    // Add these fields
    private final List<EntityObserver> observers = new ArrayList<>();

    // Add these methods
    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) {
            o.onEvent(action, payload);
        }
    }


    // ─── [S4-F11] Record Ticket Scan (write) ─────────────────────────────────
    @CacheEvict(value = "ticket-service::S4-F10", allEntries = true)
    public void recordScan(Long ticketId, String token, TicketScanRequestDTO request) {

        MDC.put("ticketId", String.valueOf(ticketId));
        MDC.put("scanType", request.getScanType());

        try {

            log.info("Recording ticket scan");

            // 1. Validate JWT
            Claims claims;

            try {
                claims = jwtService.extractClaims(token);
                log.info("JWT token validated successfully");
            } catch (Exception e) {
                log.error("JWT validation failed", e);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing token");
            }

            String role = (String) claims.get("role");

            if (role == null) {
                log.warn("JWT token missing role claim");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }

            log.info("Authenticated user with role={}", role);

            // 2. Fetch ticket
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> {
                        log.warn("Ticket not found");
                        return new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Ticket not found: " + ticketId
                        );
                    });

            log.info("Ticket found for attendee={}", ticket.getAttendeeName());

            // 3. Create Cassandra entity
            TicketScanEventKey key = new TicketScanEventKey(ticketId, Instant.now());

            TicketScanEvent event = new TicketScanEvent();
            event.setKey(key);
            event.setScanType(request.getScanType());
            event.setAttendeeName(ticket.getAttendeeName());
            event.setGate(request.getGate());
            event.setSection(request.getSection());
            event.setSeatNumber(request.getSeatNumber());
            event.setNotes(request.getNotes());

            log.info("Saving scan event to Cassandra");

            // 4. Save Cassandra
            cassandraRepository.save(event);

            log.info("Scan event saved successfully");

            cacheEviction.evictPattern("S4-F12");

            log.info("Evicted cache pattern S4-F12");

            // 5. Mongo logging
            Map<String, Object> payload = new HashMap<>();
            payload.put("ticketId", ticketId);
            payload.put("scanType", request.getScanType());
            payload.put("gate", request.getGate());
            payload.put("section", request.getSection());
            payload.put("seatNumber", request.getSeatNumber());
            payload.put("notes", request.getNotes());

            mongoEventLogger.onEvent("TRACKING_RECORDED", payload);

            log.info("Mongo event log recorded successfully");

        } catch (Exception e) {

            log.error("Failed to record ticket scan", e);
            throw e;

        } finally {

            MDC.clear();
        }


    }

    @Cacheable(
            cacheNames = "ticket-service::S4-F12",
            key = "#ticketId + ':' + T(String).valueOf(#startTime)+ ':' + T(String).valueOf(#endTime)"
    )
    public List<TicketScanDTO> getScanHistory(Long ticketId, Instant startTime, Instant endTime) {


        MDC.put("ticketId", String.valueOf(ticketId));

        try {

            log.info(
                    "Fetching ticket scan history with startTime={} and endTime={}",
                    startTime,
                    endTime
            );

            // Verify ticket exists
            ticketRepository.findById(ticketId)
                    .orElseThrow(() -> {
                        log.warn("Ticket not found while fetching scan history");
                        return new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Ticket not found: " + ticketId
                        );
                    });

            List<TicketScanEvent> events;

            // Query Cassandra
            if (startTime != null && endTime != null) {

                log.info("Querying Cassandra using BETWEEN range");

                events = cassandraRepository
                        .findByKeyTicketIdAndKeyTimestampBetween(
                                ticketId,
                                startTime,
                                endTime
                        );

            } else if (startTime != null) {

                log.info("Querying Cassandra using startTime filter");

                events = cassandraRepository
                        .findByKeyTicketIdAndKeyTimestampGreaterThanEqual(
                                ticketId,
                                startTime
                        );

            } else if (endTime != null) {

                log.info("Querying Cassandra using endTime filter");

                events = cassandraRepository
                        .findByKeyTicketIdAndKeyTimestampLessThanEqual(
                                ticketId,
                                endTime
                        );

            } else {

                log.info("Querying Cassandra for full ticket scan history");

                events = cassandraRepository.findByKeyTicketId(ticketId);
            }

            log.info("Retrieved {} scan events from Cassandra", events.size());

            // Adapt to DTOs
            CassandraRowAdapter adapter = new CassandraRowAdapter();

            List<TicketScanDTO> result = events.stream()
                    .map(adapter::adapt)
                    .collect(Collectors.toList());

            log.info("Successfully adapted scan events to DTOs");

            return result;

        } catch (Exception e) {

            log.error("Failed to fetch ticket scan history", e);
            throw e;

        } finally {

            MDC.clear();
        }

    }

}