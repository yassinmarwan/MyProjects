package com.team22.eventticketing.ticket.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;


@PrimaryKeyClass
public class TicketScanEventKey implements Serializable {
    
    @PrimaryKeyColumn(name ="ticket_id", type=PrimaryKeyType.PARTITIONED)
    private Long ticketId;

    @PrimaryKeyColumn(name = "timestamp", type=PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant timestamp;
    public TicketScanEventKey() {}
    public TicketScanEventKey(Long ticketId, Instant timestamp) {
        this.ticketId = ticketId;
        this.timestamp = timestamp;
    }
    public Long getTicketId() {return ticketId;}
    public void setTicketId(Long id) {this.ticketId = id;}
    public Instant getTimestamp() {return timestamp;}
    public void setTimestamp(Instant ts) {this.timestamp = ts; }

    @Override public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof TicketScanEventKey that)) return false;
        return Objects.equals( ticketId, that.ticketId) && Objects.equals(timestamp, that.timestamp);
    }

    @Override public int hashCode() {return Objects.hash(ticketId, timestamp);}
    
}
