package com.team22.eventticketing.booking.repository;

import com.team22.eventticketing.booking.model.Booking;
import com.team22.eventticketing.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Method 1: When status is provided
    List<Booking> findByStatusAndBookingDateBetweenOrderByBookingDateDesc(
            BookingStatus status, LocalDateTime startDate, LocalDateTime endDate);

    // Method 2: When status is NOT provided (fallback)
    List<Booking> findByBookingDateBetweenOrderByBookingDateDesc(
            LocalDateTime startDate, LocalDateTime endDate);

    // S3-F2: Check Event Status
    @Query(value = "SELECT status FROM events WHERE id = :eventId", nativeQuery = true)
    String findEventStatusById(@Param("eventId") Long eventId);

    // S3-F3: Get Average Session Capacity for an Event
    @Query(value = "SELECT AVG(capacity) FROM event_sessions WHERE event_id = :eventId", nativeQuery = true)
    Double getAverageSessionCapacity(@Param("eventId") Long eventId);

    // S3-F3: Get Active Bookings Count for Demand Multiplier
    @Query(value = "SELECT COUNT(*) FROM bookings WHERE event_id = :eventId AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')", nativeQuery = true)
    Long countActiveBookingsForEvent(@Param("eventId") Long eventId);

    List<Booking> findByUserId(Long userId);

   // long countByUserIdAndStatus(Long userId, BookingStatus status);

    // S3-F4: load booking with items for calculation
    @Query("SELECT b FROM Booking b LEFT JOIN FETCH b.bookingItems WHERE b.id = :id")
    Optional<Booking> findByIdWithItems(@Param("id") Long id);

    // S3-F4: insert a PENDING ticket sale into shared ticket_sales table
    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO ticket_sales (booking_id, user_id, amount, method, status, created_at, transaction_details)
    VALUES (:bookingId, :userId, :amount, 'CREDIT_CARD', 'PENDING', NOW(), '{}')
    """, nativeQuery = true)
    void insertTicketSale(@Param("bookingId") Long bookingId,
                          @Param("userId") Long userId,
                          @Param("amount") Double amount);

    // S3-F5: filter bookings by JSONB metadata key-value pair
    @Query(value = "SELECT * FROM bookings WHERE metadata->>:key = :value",
            nativeQuery = true)
    List<Booking> findByMetadataField(@Param("key") String key,
                                      @Param("value") String value);

    // S3-F6: count all bookings in date range
    @Query(value = """
    SELECT COUNT(*) FROM bookings
    WHERE booking_date BETWEEN :start AND :end
    """, nativeQuery = true)
    long countInRange(@Param("start") LocalDateTime start,
                      @Param("end") LocalDateTime end);

    // S3-F6: count completed bookings in date range
    @Query(value = """
    SELECT COUNT(*) FROM bookings
    WHERE booking_date BETWEEN :start AND :end AND status = 'COMPLETED'
    """, nativeQuery = true)
    long countCompletedInRange(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    // S3-F6: count cancelled bookings in date range
    @Query(value = """
    SELECT COUNT(*) FROM bookings
    WHERE booking_date BETWEEN :start AND :end AND status = 'CANCELLED'
    """, nativeQuery = true)
    long countCancelledInRange(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    // S3-F6: sum revenue from completed bookings in date range
    @Query(value = """
    SELECT COALESCE(SUM(total_amount), 0) FROM bookings
    WHERE booking_date BETWEEN :start AND :end AND status = 'COMPLETED'
    """, nativeQuery = true)
    double sumRevenueInRange(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Query(value = """
    SELECT 
        COUNT(*) as totalBookings,
        COALESCE(SUM(total_amount), 0) as totalRevenue,
        COALESCE(AVG(total_amount), 0) as averageBookingAmount
    FROM bookings
    WHERE event_id = :eventId
    AND status = 'COMPLETED'
    AND booking_date BETWEEN :start AND :end
    """, nativeQuery = true)
    Object[] getEventRevenueStats(@Param("eventId") Long eventId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE tickets SET status = 'CANCELLED'
    WHERE booking_id = :bookingId AND status = 'VALID'
    """, nativeQuery = true)
    void cancelValidTicketsForBooking(@Param("bookingId") Long bookingId);

    @Query(value = """
    SELECT COALESCE(MAX(event_order), 0) FROM booking_items
    WHERE booking_id = :bookingId
    """, nativeQuery = true)
    int findMaxEventOrderByBookingId(@Param("bookingId") Long bookingId);

    @Query(value = """
    SELECT 
        status, 
        COUNT(*) as statusCount, 
        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0) as statusRevenue
    FROM bookings 
    WHERE booking_date BETWEEN :start AND :end
    GROUP BY status
    """, nativeQuery = true)
    List<Object[]> getDashboardAggregation(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);


    // S3-F11: Fetch cross-service user data natively
    @Query(value = "SELECT name FROM users WHERE id = :userId", nativeQuery = true)
    String findUserNameById(@Param("userId") Long userId);

    // S3-F11: Fetch cross-service event data natively
    @Query(value = "SELECT name, category,event_date  FROM events WHERE id = :eventId", nativeQuery = true)
    List<Object[]> findEventDetailsById(@Param("eventId") Long eventId);


    // f 12
    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE id = :userId", nativeQuery = true)
    boolean existsUserById(@Param("userId") Long userId);

   // milestone 3
   // count all bookings for a user
   Long countByUserId(Long userId);

    // count bookings for a user filtered by status
    Long countByUserIdAndStatus(Long userId, BookingStatus status);

    // sum of completed booking amounts for a user
    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.userId = :userId AND b.status = :status")
    BigDecimal sumTotalAmountByUserIdAndStatus(@Param("userId") Long userId, @Param("status") BookingStatus status);

    // count active bookings for a user (active = not finished)
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.userId = :userId AND b.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETING', 'PAYMENT_PENDING')")
    Integer countActiveBookingsByUserId(@Param("userId") Long userId);

    // sum of completed booking amounts for a user in date range
    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.userId = :userId AND b.status = 'COMPLETED' AND b.bookingDate BETWEEN :start AND :end")
    BigDecimal sumCompletedAmountByUserIdInRange(@Param("userId") Long userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // count active bookings for an event
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.eventId = :eventId AND b.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETING', 'PAYMENT_PENDING')")
    Integer countActiveBookingsByEventId(@Param("eventId") Long eventId);

    // count completed bookings for an event in date range
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.eventId = :eventId AND b.status = 'COMPLETED' AND b.bookingDate BETWEEN :start AND :end")
    Long countCompletedByEventIdInRange(@Param("eventId") Long eventId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // sum of completed booking amounts for an event in date range
    @Query("SELECT SUM(b.totalAmount) FROM Booking b WHERE b.eventId = :eventId AND b.status = 'COMPLETED' AND b.bookingDate BETWEEN :start AND :end")
    BigDecimal sumCompletedAmountByEventIdInRange(@Param("eventId") Long eventId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}