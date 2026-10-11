//package com.team22.eventticketing.ticket.repository;
//
//import com.team22.eventticketing.ticket.model.Ticket;
//import com.team22.eventticketing.ticket.model.TicketStatus;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Modifying;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//import java.util.Map;
//
//@Repository
//public interface TicketRepository extends JpaRepository<Ticket, Long> {
//
//    // get latest ticket
//    Optional<Ticket> findTopByBookingIdOrderByIssuedAtDesc(Long bookingId);
//    List<Ticket> findByStatus(TicketStatus status);
//    // Check if booking exists (cross-table query)
//    @Query(value = "SELECT COUNT(*) > 0 FROM bookings WHERE id = :bookingId", nativeQuery = true)
//    boolean existsBookingById(@Param("bookingId") Long bookingId);
//
//    boolean existsByTicketCode(String ticketCode);
//
//    @Query(value = """
//                SELECT
//                    t.id AS ticketId,
//                    t.attendee_name AS attendeeName,
//                    t.booking_id AS bookingId,
//                    e.name AS eventName,
//
//                    CAST(e.details->>'venueLat' AS DOUBLE PRECISION) AS eventLat,
//                    CAST(e.details->>'venueLon' AS DOUBLE PRECISION) AS eventLon,
//
//                    (
//                        SQRT(
//                            POWER(CAST(e.details->>'venueLat' AS DOUBLE PRECISION) - :lat, 2) +
//                            POWER(CAST(e.details->>'venueLon' AS DOUBLE PRECISION) - :lon, 2)
//                        ) * 111
//                    ) AS distanceKm
//
//                FROM tickets t
//                JOIN bookings b ON t.booking_id = b.id
//                JOIN events e ON b.event_id = e.id
//
//                WHERE t.status = 'VALID'
//                AND (
//                    SQRT(
//                        POWER(CAST(e.details->>'venueLat' AS DOUBLE PRECISION) - :lat, 2) +
//                        POWER(CAST(e.details->>'venueLon' AS DOUBLE PRECISION) - :lon, 2)
//                    ) * 111
//                ) <= :radiusKm
//
//                ORDER BY distanceKm ASC
//            """, nativeQuery = true)
//    List<Object[]> findNearbyTickets(
//            @Param("lat") double lat,
//            @Param("lon") double lon,
//            @Param("radiusKm") double radiusKm);
//
//    Optional<Ticket> findById(Long id);
//
//    /**
//     * Find all tickets associated with a specific booking.
//     */
//    List<Ticket> findByBookingId(Long bookingId);
//
//    /**
//     * Find a ticket by its unique generated code.
//     */
//    Optional<Ticket> findByTicketCode(String ticketCode);
//
//    @Query(value = "SELECT COUNT(*) > 0 FROM bookings WHERE id = :bookingId", nativeQuery = true)
//    boolean bookingExistsById(@Param("bookingId") Long bookingId);
//
//    /**
//     * [S4-F7] Purge Expired Tickets.
//     * Deletes tickets older than a specific date if they are EXPIRED or CANCELLED.
//     */
//    @Modifying
//    @Transactional
//    @Query(value = "DELETE FROM tickets WHERE issued_at < :cutoff " +
//            "AND status IN ('EXPIRED', 'CANCELLED')", nativeQuery = true)
//    int purgeExpiredAndCancelledTickets(@Param("cutoff") LocalDateTime cutoff);
//
//    // [S4-F8] Update Ticket Status on Check-In.
//    @Query(value = "SELECT " +
//            "COUNT(*) as total, " +
//            "COUNT(*) FILTER (WHERE t.status = 'USED') as used, " +
//            "COUNT(*) FILTER (WHERE t.status = 'VALID') as _valid, " +
//            "MAX(t.issued_at) FILTER (WHERE t.status = 'USED') as last_checkin " +
//            "FROM tickets t " +
//            "JOIN bookings b ON t.booking_id = b.id " +
//            "WHERE b.event_id = :eventId", nativeQuery = true)
//    Map<String, Object> getAttendanceSummaryData(@Param("eventId") Long eventId);
//
//    // [S4-F9] Find Unused Tickets for Upcoming Events.
//
//    @Query(value = "SELECT t.id as ticketId, t.attendee_name as attendeeName, " +
//            "t.ticket_code as ticketCode, t.booking_id as bookingId, " +
//            "e.name as eventName, e.event_date as eventDate " +
//            "FROM tickets t " +
//            "JOIN bookings b ON t.booking_id = b.id " +
//            "JOIN events e ON b.event_id = e.id " +
//            "WHERE t.status = 'VALID' AND e.status = 'UPCOMING'", nativeQuery = true)
//    List<Map<String, Object>> findUnusedUpcomingTicketsNative();
//
//    // [S4-F5] Filter Tickets by Metadata (JSONB Query).
//
//    @Query(value = "SELECT * FROM tickets WHERE metadata ->> :key = :value", nativeQuery = true)
//    List<Ticket> findByMetadataEquals(@Param("key") String key, @Param("value") String value);
//
//    @Query(value = "SELECT * FROM tickets WHERE (metadata ->> :key)::numeric > CAST(:value AS numeric)", nativeQuery = true)
//    List<Ticket> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);
//
//    @Query(value = "SELECT * FROM tickets WHERE (metadata ->> :key)::numeric < CAST(:value AS numeric)", nativeQuery = true)
//    List<Ticket> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);
//
//    // [S4-F6] Get Tickets in Date Range.
//
//    @Query(value = "SELECT * FROM tickets WHERE issued_at BETWEEN :start AND :end " +
//            "ORDER BY issued_at ASC", nativeQuery = true)
//    List<Ticket> findByIssuedAtBetween(@Param("start") LocalDateTime start,
//            @Param("end") LocalDateTime end);
//
//    @Query(value = "SELECT * FROM tickets WHERE issued_at BETWEEN :start AND :end " +
//            "AND status::text = :status " +
//            "ORDER BY issued_at ASC", nativeQuery = true)
//    List<Ticket> findByIssuedAtBetweenAndStatus(@Param("start") LocalDateTime start,
//            @Param("end") LocalDateTime end,
//            @Param("status") String status);
//
//    // Fetches all tickets where issuedAt falls within the fully-closed range
//    // [S4-F10]
//    List<Ticket> findAllByIssuedAtBetween(LocalDateTime start, LocalDateTime end);
//
//
//    List<Ticket> findByEventId(Long eventId);
//
//    long countByEventId(Long eventId);
//
//    long countByEventIdAndStatus(Long eventId, TicketStatus status);
//
//
//}


package com.team22.eventticketing.ticket.repository;

import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // ─── Basic Queries ─────────────────────────────────────────────

    Optional<Ticket> findTopByBookingIdOrderByIssuedAtDesc(Long bookingId);

    Optional<Ticket> findByTicketCode(String ticketCode);

    List<Ticket> findByBookingId(Long bookingId);

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByEventId(Long eventId);

    boolean existsByTicketCode(String ticketCode);

    // ─── [S4-F7] Purge Expired Tickets ────────────────────────────

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM tickets
            WHERE issued_at < :cutoff
            AND status IN ('EXPIRED', 'CANCELLED')
            """, nativeQuery = true)
    int purgeExpiredAndCancelledTickets(
            @Param("cutoff") LocalDateTime cutoff
    );

    // ─── [S4-F5] Filter Tickets by Metadata ──────────────────────

    @Query(value = """
            SELECT * FROM tickets
            WHERE metadata ->> :key = :value
            """, nativeQuery = true)
    List<Ticket> findByMetadataEquals(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT * FROM tickets
            WHERE (metadata ->> :key)::numeric > CAST(:value AS numeric)
            """, nativeQuery = true)
    List<Ticket> findByMetadataGreaterThan(
            @Param("key") String key,
            @Param("value") String value
    );

    @Query(value = """
            SELECT * FROM tickets
            WHERE (metadata ->> :key)::numeric < CAST(:value AS numeric)
            """, nativeQuery = true)
    List<Ticket> findByMetadataLessThan(
            @Param("key") String key,
            @Param("value") String value
    );

    // ─── [S4-F6] Ticket History ──────────────────────────────────

    @Query(value = """
            SELECT * FROM tickets
            WHERE issued_at BETWEEN :start AND :end
            ORDER BY issued_at ASC
            """, nativeQuery = true)
    List<Ticket> findByIssuedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = """
            SELECT * FROM tickets
            WHERE issued_at BETWEEN :start AND :end
            AND status::text = :status
            ORDER BY issued_at ASC
            """, nativeQuery = true)
    List<Ticket> findByIssuedAtBetweenAndStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") String status
    );

    // ─── [S4-F10] Analytics ──────────────────────────────────────

    List<Ticket> findAllByIssuedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    long countByEventId(Long eventId);

    long countByEventIdAndStatus(
            Long eventId,
            TicketStatus status
    );

    List<Ticket> findByBookingIdAndStatus(
            Long bookingId,
            TicketStatus status
    );
}

