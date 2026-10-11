package com.team22.eventticketing.event.adapter;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.team22.eventticketing.event.dto.TopEventDTO;
import com.team22.eventticketing.event.model.EventSearchDocument;

public class ElasticsearchHitAdapter {

    public TopEventDTO adaptToTopEventDTO(Hit<EventSearchDocument> hit) {
        EventSearchDocument source = hit.source();
        if (source == null) return null;

        return TopEventDTO.builder()
                .eventId(Long.parseLong(source.getId()))
                .name(source.getName())
                .rating(source.getRating())
                .totalBookings(0L)
                .build();
    }

    public TopEventDTO adapt(Hit<EventSearchDocument> hit) {
        return adaptToTopEventDTO(hit);
    }
}