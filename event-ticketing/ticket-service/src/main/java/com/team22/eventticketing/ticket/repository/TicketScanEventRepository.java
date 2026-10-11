package com.team22.eventticketing.ticket.repository;

 import com.team22.eventticketing.ticket.model.TicketScanEvent;
 import com.team22.eventticketing.ticket.model.TicketScanEventKey;
 import org.springframework.data.cassandra.repository.CassandraRepository;
 import org.springframework.stereotype.Repository;
 import java.time.Instant;
 import java.util.List;

@Repository
public interface TicketScanEventRepository extends CassandraRepository<TicketScanEvent, TicketScanEventKey> {

        List<TicketScanEvent> findByKeyTicketId(Long ticketId);
        List<TicketScanEvent> findByKeyTicketIdAndKeyTimestampGreaterThanEqual(Long ticketId, Instant startTime);
        List<TicketScanEvent> findByKeyTicketIdAndKeyTimestampLessThanEqual( Long ticketId, Instant endTime);
        List<TicketScanEvent> findByKeyTicketIdAndKeyTimestampBetween(Long ticketId, Instant startTime, Instant endTime);


    }
    

