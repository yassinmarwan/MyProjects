package com.team22.eventticketing.booking.repository;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class UserNodeCustomRepository {

    private final Driver driver;

    public UserNodeCustomRepository(Driver driver) {
        this.driver = driver;
    }

    public long countRecorded(Long userId, Long eventId, Long bookingId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx ->
                    tx.run("""
                    OPTIONAL MATCH (u:UserNode {userId: $userId})-[r:ATTENDED]->(e:EventNode {eventId: $eventId})
                    WHERE r.recorded_booking_ids IS NOT NULL
                      AND $bookingId IN r.recorded_booking_ids
                    RETURN COUNT(r) AS cnt
                    """,
                            Map.of("userId", userId, "eventId", eventId, "bookingId", bookingId)
                    ).single().get("cnt").asLong()
            );
        }
    }

    public void recordAttendance(Long userId, String userName,
                                 Long eventId, String eventName, String eventCategory,
                                 Long bookingId) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                    MERGE (u:UserNode {userId: $userId})
                      ON CREATE SET u.name = $userName
                    MERGE (e:EventNode {eventId: $eventId})
                      ON CREATE SET e.name = $eventName, e.category = $eventCategory
                    MERGE (u)-[r:ATTENDED]->(e)
                      ON CREATE SET r.attendanceCount = 1,
                                    r.lastAttendedDate = $now,
                                    r.recorded_booking_ids = [$bookingId]
                      ON MATCH  SET r.attendanceCount = coalesce(r.attendanceCount, 0) + 1,
                                    r.lastAttendedDate = $now,
                                    r.recorded_booking_ids = coalesce(r.recorded_booking_ids, []) + $bookingId
                    """,
                        Map.of(
                                "userId", userId,
                                "userName", userName,
                                "eventId", eventId,
                                "eventName", eventName,
                                "eventCategory", eventCategory,
                                "bookingId", bookingId,
                                "now", Values.value(LocalDateTime.now())
                        )
                );
                return null;
            });
        }
    }
}