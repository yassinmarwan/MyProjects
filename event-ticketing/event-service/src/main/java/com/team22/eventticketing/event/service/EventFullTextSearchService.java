package com.team22.eventticketing.event.service;

import com.team22.eventticketing.event.model.EventSearchDocument;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventFullTextSearchService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ELASTICSEARCH_SEARCH_URL =
            "http://elasticsearch:9200/events/_search";

    @Cacheable(
            cacheNames = "event-service::S2-F10",
            key = "T(String).valueOf(#query) + ':' + " +
                    "T(String).valueOf(#category) + ':' + " +
                    "T(String).valueOf(#venue) + ':' + " +
                    "T(String).valueOf(#status) + ':' + " +
                    "T(String).valueOf(#startDate) + ':' + " +
                    "T(String).valueOf(#endDate) + ':' + " +
                    "T(String).valueOf(#minRating) + ':' + " +
                    "T(String).valueOf(#maxRating)"
    )
    public List<EventSearchDocument> fullTextSearch(
            String query,
            String category,
            String venue,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double minRating,
            Double maxRating
    ) {
        Map<String, Object> requestBody = buildSearchRequest(
                query,
                category,
                venue,
                status,
                startDate,
                endDate,
                minRating,
                maxRating
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        Map response = restTemplate.postForObject(
                ELASTICSEARCH_SEARCH_URL,
                request,
                Map.class
        );

        return extractResults(response);
    }

    private Map<String, Object> buildSearchRequest(
            String query,
            String category,
            String venue,
            String status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Double minRating,
            Double maxRating
    ) {
        boolean hasQuery = query != null && !query.isBlank();

        List<Map<String, Object>> mustQueries = new ArrayList<>();
        List<Map<String, Object>> filters = new ArrayList<>();

        if (hasQuery) {
            String safeQuery = query.trim().toLowerCase();

            mustQueries.add(Map.of(
                    "query_string", Map.of(
                            "query", "*" + safeQuery + "*",
                            "fields", List.of("name", "description", "venue"),
                            "analyze_wildcard", true,
                            "default_operator", "OR"
                    )
            ));
        } else {
            mustQueries.add(Map.of("match_all", Map.of()));
        }

        if (category != null && !category.isBlank()) {
            filters.add(Map.of(
                    "match", Map.of(
                            "category", category.trim().toUpperCase()
                    )
            ));
        }

        if (venue != null && !venue.isBlank()) {
            filters.add(Map.of(
                    "match_phrase", Map.of(
                            "venue", venue.trim()
                    )
            ));
        }

        if (status != null && !status.isBlank()) {
            filters.add(Map.of(
                    "match", Map.of(
                            "status", status.trim().toUpperCase()
                    )
            ));
        }

        if (startDate != null || endDate != null) {
            Map<String, Object> dateRange = new HashMap<>();

            if (startDate != null) {
                dateRange.put("gte", startDate.toString());
            }

            if (endDate != null) {
                dateRange.put("lte", endDate.toString());
            }

            filters.add(Map.of(
                    "range", Map.of(
                            "eventDate", dateRange
                    )
            ));
        }

        if (minRating != null || maxRating != null) {
            Map<String, Object> ratingRange = new HashMap<>();

            if (minRating != null) {
                ratingRange.put("gte", minRating);
            }

            if (maxRating != null) {
                ratingRange.put("lte", maxRating);
            }

            filters.add(Map.of(
                    "range", Map.of(
                            "rating", ratingRange
                    )
            ));
        }

        Map<String, Object> boolQuery = new HashMap<>();
        boolQuery.put("must", mustQueries);

        if (!filters.isEmpty()) {
            boolQuery.put("filter", filters);
        }

        Map<String, Object> finalQuery = Map.of(
                "bool", boolQuery
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", finalQuery);

        if (hasQuery) {
            requestBody.put("sort", List.of(
                    Map.of("_score", Map.of("order", "desc"))
            ));
        }

        return requestBody;
    }

    private List<EventSearchDocument> extractResults(Map response) {
        List<EventSearchDocument> results = new ArrayList<>();

        if (response == null || response.get("hits") == null) {
            return results;
        }

        Map hitsObject = (Map) response.get("hits");

        if (hitsObject.get("hits") == null) {
            return results;
        }

        List hits = (List) hitsObject.get("hits");

        for (Object hitObject : hits) {
            Map hit = (Map) hitObject;
            Map source = (Map) hit.get("_source");

            if (source == null) {
                continue;
            }

            EventSearchDocument doc = new EventSearchDocument();

            Object id = source.get("id");
            Object name = source.get("name");
            Object description = source.get("description");
            Object category = source.get("category");
            Object venue = source.get("venue");
            Object status = source.get("status");
            Object eventDate = source.get("eventDate");
            Object rating = source.get("rating");

            if (id != null) {
                doc.setId(String.valueOf(id));
            }

            if (name != null) {
                doc.setName(String.valueOf(name));
            }

            if (description != null) {
                doc.setDescription(String.valueOf(description));
            }

            if (category != null) {
                doc.setCategory(String.valueOf(category));
            }

            if (venue != null) {
                doc.setVenue(String.valueOf(venue));
            }

            if (status != null) {
                doc.setStatus(String.valueOf(status));
            }


            if (eventDate != null) {
                String dateStr = String.valueOf(eventDate);

                // 1. If the date is missing the time component (e.g., "2026-06-10"), append midnight
                if (!dateStr.contains("T")) {
                    dateStr += "T00:00:00";
                }

                // 2. If Elasticsearch appended a UTC 'Z' timezone marker, strip it so LocalDateTime doesn't crash
                dateStr = dateStr.replace("Z", "");

                try {
                    doc.setEventDate(LocalDateTime.parse(dateStr));
                } catch (Exception e) {
                    // 3. If a truly corrupted date gets through, log it instead of crashing the whole server
                    System.err.println("Failed to parse eventDate from Elasticsearch: " + dateStr);
                }
            }



            if (rating instanceof Number number) {
                doc.setRating(number.doubleValue());
            } else if (rating != null) {
                doc.setRating(Double.parseDouble(String.valueOf(rating)));
            }

            results.add(doc);
        }

        return results;
    }
}