package com.team22.eventticketing.ticket.model;

 import org.springframework.data.cassandra.core.mapping.Column;
 import org.springframework.data.cassandra.core.mapping.PrimaryKey;
 import org.springframework.data.cassandra.core.mapping.Table;
 import java.io.Serializable;

 @Table("ticket_scan_events")
public class TicketScanEvent implements Serializable {
    
    @PrimaryKey
    private TicketScanEventKey key;

    @Column("scan_type")        private String scanType;
    @Column("attendee_name")    private String attendeeName;
    @Column("gate")             private String gate;
    @Column("section")          private String section;
    @Column("seat_number")      private String seatNumber;
    @Column("notes")            private String notes;

    public TicketScanEvent() {}
    public TicketScanEvent(TicketScanEventKey key, String scanType, String attendeeName,
         String gate, String section, String seatNumber, String notes){

            this.key = key;
            this.scanType = scanType;
            this.attendeeName = attendeeName;
            this.gate = gate;
            this.section = section;
            this.seatNumber = seatNumber;
            this.notes = notes;

         }

    public TicketScanEventKey getKey() {return key;}
    public void setKey(TicketScanEventKey key) {this.key = key;}
    public String getScanType() {return scanType;}
    public void setScanType(String st) {this.scanType = st;}
    public String getAttendeeName() {return attendeeName;}
    public void setAttendeeName(String name) {this.attendeeName = name;}
    public String getGate() {return gate;}
    public void setGate(String gate) {this.gate = gate;}
    public String getSection() {return section;}
    public void setSection(String section) {this.section = section;}
    public String getSeatNumber() {return seatNumber;}
    public void setSeatNumber(String seat) {this.seatNumber = seat;}
    public String getNotes() {return notes;}
    public void setNotes(String notes) {this.notes = notes;}

    
}
