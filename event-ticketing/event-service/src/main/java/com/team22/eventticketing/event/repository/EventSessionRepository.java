package com.team22.eventticketing.event.repository;

import com.team22.eventticketing.event.model.EventSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSessionRepository extends JpaRepository<EventSession, Long> {

    @Query("""
           SELECT COALESCE(AVG(s.capacity), 0.0)
           FROM EventSession s
           WHERE s.event.id = :eventId
           """)
    Double findAverageCapacityByEventId(@Param("eventId") Long eventId);
}