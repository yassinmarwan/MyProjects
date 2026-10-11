package com.team22.eventticketing.event.repository;

import com.team22.eventticketing.event.model.EventSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSearchRepository extends ElasticsearchRepository<EventSearchDocument, String> {
}