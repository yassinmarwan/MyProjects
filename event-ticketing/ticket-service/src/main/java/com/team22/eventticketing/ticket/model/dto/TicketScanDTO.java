package com.team22.eventticketing.ticket.model.dto;

import java.time.Instant;

public class TicketScanDTO {
    private Instant timestamp;
    private String scanType, attendeeName, gate, section, seatNumber, notes;


    public TicketScanDTO(Builder builder) {
        this.timestamp = builder.timestamp;
        this.scanType = builder.scanType;
        this.attendeeName = builder.attendeeName;
        this.gate = builder.gate;
        this.section = builder.section;
        this.seatNumber = builder.seatNumber;
        this.notes = builder.notes;

    }

    public static Builder builder() {return new Builder();}

    public static class Builder {
        private Instant timestamp;
        private String scanType, attendeeName, gate, section, seatNumber, notes;

        public Builder timestamp(Instant ts) {this.timestamp = ts; return this;}
        public Builder scanType (String st) {this.scanType = st; return this;}
        public Builder attendeeName(String an) {this.attendeeName = an; return this;}
        public Builder gate(String g) {this.gate = g; return this;}
        public Builder section(String sc) {this.section = sc; return this;}
        public Builder seatNumber(String s) {this.seatNumber = s; return this;}
        public Builder notes(String n) {this.notes = n; return this;}
        public TicketScanDTO build() {return new TicketScanDTO(this);}


    }

    public Instant getTimestamp() {return timestamp;}
    public String getScanType() {return scanType;}
    public String getAttendeeName() {return attendeeName;}
    public String getGate() {return gate;}
    public String getSection() {return section;}
    public String getSeatNumber() {return seatNumber;}
    public String getNotes() {return notes;}
    



}
