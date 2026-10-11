package com.team22.eventticketing.ticket.repository;

import com.team22.eventticketing.ticket.model.BookingEventMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingEventMappingRepository extends JpaRepository<BookingEventMapping, Long> {
}
