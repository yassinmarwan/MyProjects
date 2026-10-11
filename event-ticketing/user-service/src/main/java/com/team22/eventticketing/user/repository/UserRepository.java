package com.team22.eventticketing.user.repository;

import com.team22.eventticketing.user.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT * FROM users WHERE " +
           "(:name IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:email IS NULL OR LOWER(email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
           "(:role IS NULL OR role::text = :role)",
           nativeQuery = true)
    List<User> searchUsers(@Param("name") String name,
                           @Param("email") String email,
                           @Param("role") String role);

    // S1-F3: aggregate booking stats for a user from the shared bookings table
    @Query(value = """
        SELECT
            COUNT(b.id)                                                          AS totalBookings,
            COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedBookings,
            COALESCE(SUM(CASE WHEN b.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledBookings,
            COALESCE(SUM(CASE WHEN b.status = 'COMPLETED' THEN b.total_amount ELSE 0 END), 0) AS totalSpent,
            COALESCE(AVG(CASE WHEN b.status = 'COMPLETED' THEN b.total_amount END), 0)        AS averageBookingAmount
        FROM bookings b
        WHERE b.user_id = :userId
        """, nativeQuery = true)
    List<Object[]> getBookingSummaryByUserId(@Param("userId") Long userId);

    // S1-F4: count active (non-terminal) bookings for a user in the shared bookings table
    @Query(value = """
        SELECT COUNT(*) FROM bookings
        WHERE user_id = :userId
          AND status NOT IN ('COMPLETED', 'CANCELLED')
        """, nativeQuery = true)
    long countActiveBookingsByUserId(@Param("userId") Long userId);

    // S1-F5: all users (preference filtering done in-service for DB portability)
    @Query("SELECT u FROM User u")
    List<User> findAllUsers();

    // S1-F6: top N attendees by total spending in a date range
    @Query(value = """
        SELECT u.id AS userId, u.name AS name,
               COALESCE(SUM(b.total_amount), 0) AS totalSpent,
               COUNT(b.id) AS bookingCount
        FROM users u
        JOIN bookings b ON b.user_id = u.id
        WHERE b.booking_date BETWEEN :start AND :end
          AND b.status = 'COMPLETED'
        GROUP BY u.id, u.name
        ORDER BY totalSpent DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopAttendees(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("limit") int limit);

    // S1-F8
    @Transactional
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.favoriteVenues WHERE u.id = :id")
    Optional<User> findByIdWithVenues(@Param("id") Long id);


    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    // S1-F9: completed booking count per user (category filtering done in-service)
    @Query(value = """
        SELECT b.user_id, COUNT(b.id)
        FROM bookings b
        WHERE b.status = 'COMPLETED'
        GROUP BY b.user_id
        """, nativeQuery = true)
    List<Object[]> countCompletedBookingsPerUser();

    @Modifying
    @Transactional
    @Query(value = "ALTER SEQUENCE users_id_seq RESTART WITH 10000", nativeQuery = true)
    void resetSequence();
}
