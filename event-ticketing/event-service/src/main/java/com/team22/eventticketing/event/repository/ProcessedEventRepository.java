package com.team22.eventticketing.event.repository;

import com.team22.eventticketing.event.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}