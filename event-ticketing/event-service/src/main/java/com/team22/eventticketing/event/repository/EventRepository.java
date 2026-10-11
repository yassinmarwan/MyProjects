package com.team22.eventticketing.event.repository;

import com.team22.eventticketing.event.model.Event;
import com.team22.eventticketing.event.model.EventCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Local event-service query only: events table JSONB
    @Query(value = """
            SELECT * FROM events e
            WHERE e.details ->> :key = :value
            """, nativeQuery = true)
    List<Event> findByDetailAttribute(
            @Param("key") String key,
            @Param("value") String value
    );

    // Local event-service query only: events table JSONB + local status
    @Query(value = """
            SELECT * FROM events e
            WHERE e.details ->> :key = :value
              AND e.status = :status
            """, nativeQuery = true)
    List<Event> findByDetailAttributeAndStatus(
            @Param("key") String key,
            @Param("value") String value,
            @Param("status") String status
    );

    List<Event> findByCategoryOrderByEventDateAsc(EventCategory category);

    List<Event> findByCategoryAndEventDateBetweenOrderByEventDateAsc(
            EventCategory category,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Event> findByEventDateBetweenOrderByEventDateAsc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Event> findAllByOrderByEventDateAsc();

    List<Event> findByCategoryAndEventDateAfterOrderByEventDateAsc(
            EventCategory category,
            LocalDateTime startDate
    );

    List<Event> findByCategoryAndEventDateBeforeOrderByEventDateAsc(
            EventCategory category,
            LocalDateTime endDate
    );

    List<Event> findByEventDateAfterOrderByEventDateAsc(LocalDateTime startDate);

    List<Event> findByEventDateBeforeOrderByEventDateAsc(LocalDateTime endDate);

    // Local event-service query only: Event -> EventSession relationship
    @Query("SELECT DISTINCT e FROM Event e JOIN e.eventSessions s WHERE s.verified = false")
    List<Event> findEventsWithUnverifiedSessions();

    // Local event-service query only: rating is stored on Event
    List<Event> findAllByOrderByRatingDesc(Pageable pageable);
}