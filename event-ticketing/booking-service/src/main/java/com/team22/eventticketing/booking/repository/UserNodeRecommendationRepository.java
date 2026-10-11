package com.team22.eventticketing.booking.repository;

import org.neo4j.driver.Record;

import java.util.List;

public interface UserNodeRecommendationRepository {
    List<Record> findRecommendations(Long userId, int limit);
}