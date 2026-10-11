package com.team22.eventticketing.booking.repository;

import com.team22.eventticketing.booking.model.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {
}