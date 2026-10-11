package com.team22.eventticketing.ticket.model.dto;

public class TicketScanRequestDTO {
    private String scanType, gate, section, seatNumber, notes;

    public String getScanType() {return scanType;}
    public void setScanType(String st) {this.scanType = st;}
    public String getGate() {return gate;}
    public void setGate(String gate) {this.gate = gate;}
    public String getSection() {return section;}
    public void setSection(String section) {this.section = section;}
    public String getSeatNumber() {return seatNumber;}
    public void setSeatNumber(String seat) {this.seatNumber = seat;}
    public String getNotes() {return notes;}
    public void setNotes(String notes) {this.notes = notes;}
}
