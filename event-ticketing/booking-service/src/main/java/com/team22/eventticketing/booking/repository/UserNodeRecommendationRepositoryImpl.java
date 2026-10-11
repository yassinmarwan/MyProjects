package com.team22.eventticketing.booking.repository;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class UserNodeRecommendationRepositoryImpl implements UserNodeRecommendationRepository {

    private final Driver driver;

    public UserNodeRecommendationRepositoryImpl(Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<Record> findRecommendations(Long userId, int limit) {

        String query = """
            MATCH (u:UserNode)-[:ATTENDED]->(e:EventNode)<-[:ATTENDED]-(other:UserNode)
            WHERE u.userId = $userId AND other.userId <> $userId

            MATCH (other)-[:ATTENDED]->(rec:EventNode)
            WHERE NOT (u)-[:ATTENDED]->(rec)

            RETURN rec.eventId AS eventId,
                   COUNT(DISTINCT other) AS score
            ORDER BY score DESC
            LIMIT $limit
        """;

        try (Session session = driver.session()) {
            return session.run(query, Map.of(
                    "userId", userId,
                    "limit", limit
            )).list();
        }
    }
}