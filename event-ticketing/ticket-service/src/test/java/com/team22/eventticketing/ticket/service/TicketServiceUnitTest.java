package com.team22.eventticketing.ticket.service;

import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.dto.EventDTO;
import com.team22.eventticketing.contracts.dto.EventTicketSummaryDTO;
import com.team22.eventticketing.contracts.dto.VenueCoordsDTO;
import com.team22.eventticketing.contracts.events.TicketIssuedEvent;
import com.team22.eventticketing.contracts.feign.EventServiceClient;
import com.team22.eventticketing.ticket.messaging.TicketEventPublisher;
import com.team22.eventticketing.ticket.resilience.BookingServiceFacade;
import com.team22.eventticketing.ticket.model.BookingEventMapping;
import com.team22.eventticketing.ticket.model.Ticket;
import com.team22.eventticketing.ticket.model.TicketStatus;
import com.team22.eventticketing.ticket.model.dto.BatchTicketRequestDTO;
import com.team22.eventticketing.ticket.model.dto.NearbyTicketDTO;
import com.team22.eventticketing.ticket.model.dto.TicketItemDTO;
import com.team22.eventticketing.ticket.model.dto.UnusedTicketDTO;
import com.team22.eventticketing.ticket.repository.BookingEventMappingRepository;
import com.team22.eventticketing.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceUnitTest {

    @Mock TicketRepository ticketRepository;
    @Mock CacheEvictionService cacheEviction;
    @Mock BookingServiceFacade bookingServiceFacade;
    @Mock EventServiceClient eventServiceClient;
    @Mock TicketEventPublisher ticketEventPublisher;
    @Mock BookingEventMappingRepository bookingEventMappingRepository;

    @InjectMocks TicketService ticketService;

    private static BookingDTO bookingDto(Long bookingId, Long eventId) {
        return new BookingDTO(bookingId, 10L, eventId, "CHECKED_IN", BigDecimal.valueOf(500.00));
    }

    @Test
    void f2_issueTicket_callsFeignThenSavesAndPublishesTicketIssued() {
        Long bookingId = 1L;
        Long eventId = 99L;

        when(bookingServiceFacade.getBooking(bookingId)).thenReturn(bookingDto(bookingId, eventId));
        when(bookingEventMappingRepository.findById(bookingId))
                .thenReturn(Optional.of(new BookingEventMapping(bookingId, eventId, java.time.LocalDateTime.now())));
        when(ticketRepository.existsByTicketCode("TKT-001")).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        Ticket saved = ticketService.issueTicket(bookingId, "Alice", "TKT-001", Map.of());

        verify(bookingServiceFacade, times(1)).getBooking(bookingId);
        verify(ticketRepository).save(any(Ticket.class));
        verify(ticketEventPublisher).publishTicketIssued(any(TicketIssuedEvent.class));
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    void f2_issueTicket_feignThrows_mapsTo404AndDoesNotPublish() {
        Long bookingId = 999L;
        when(bookingServiceFacade.getBooking(bookingId)).thenThrow(new RuntimeException("404 Not Found"));

        assertThatThrownBy(() -> ticketService.issueTicket(bookingId, "Alice", "TKT-X", Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(ticketEventPublisher, never()).publishTicketIssued(any());
    }

    @Test
    void f8_getEventTicketSummary_readsLocallyAndReturnsCounts() {
        Long eventId = 5L;
        Ticket t1 = new Ticket();
        t1.setStatus(TicketStatus.USED);
        Ticket t2 = new Ticket();
        t2.setStatus(TicketStatus.USED);
        Ticket t3 = new Ticket();
        t3.setStatus(TicketStatus.VALID);

        when(ticketRepository.findByEventId(eventId)).thenReturn(List.of(t1, t2, t3));

        EventTicketSummaryDTO summary = ticketService.getEventTicketSummary(eventId);

        verify(ticketRepository, times(1)).findByEventId(eventId);
        verify(bookingServiceFacade, never()).getBooking(anyLong());
        verify(eventServiceClient, never()).getEvent(anyLong());
        assertThat(summary.totalTicketsSold()).isEqualTo(3L);
        assertThat(summary.usedCount()).isEqualTo(2L);
    }

    @Test
    void f8_getEventTicketSummary_noTickets_throws404() {
        Long eventId = 404L;
        when(ticketRepository.findByEventId(eventId)).thenReturn(List.of());

        assertThatThrownBy(() -> ticketService.getEventTicketSummary(eventId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tickets found");
    }

    // ─── F1: getLatestTicketByBooking — Feign existence check + local query ────

    @Test
    void f1_getLatestTicketByBooking_callsFeignThenLocalQuery() {
        Long bookingId = 1L;
        Ticket latest = new Ticket();
        latest.setId(100L);
        latest.setBookingId(bookingId);

        when(bookingServiceFacade.getBooking(bookingId)).thenReturn(bookingDto(bookingId, 99L));
        when(ticketRepository.findTopByBookingIdOrderByIssuedAtDesc(bookingId))
                .thenReturn(Optional.of(latest));

        Ticket result = ticketService.getLatestTicketByBooking(bookingId);

        verify(bookingServiceFacade, times(1)).getBooking(bookingId);
        verify(ticketRepository, times(1)).findTopByBookingIdOrderByIssuedAtDesc(bookingId);
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void f1_getLatestTicketByBooking_feignThrows_mapsTo404() {
        Long bookingId = 999L;
        when(bookingServiceFacade.getBooking(bookingId)).thenThrow(new RuntimeException("404"));

        assertThatThrownBy(() -> ticketService.getLatestTicketByBooking(bookingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verify(ticketRepository, never()).findTopByBookingIdOrderByIssuedAtDesc(anyLong());
    }

    // ─── F4: batchIssueTickets — Feign + local persist + per-ticket publish ────

    @Test
    void f4_batchIssueTickets_callsFeignThenSavesAndPublishesPerTicket() {
        Long bookingId = 1L;
        Long eventId = 99L;
        BatchTicketRequestDTO request = new BatchTicketRequestDTO(bookingId, List.of(
                new TicketItemDTO("Alice", "TKT-001", Map.of()),
                new TicketItemDTO("Bob", "TKT-002", Map.of())));

        when(bookingServiceFacade.getBooking(bookingId)).thenReturn(bookingDto(bookingId, eventId));
        when(bookingEventMappingRepository.findById(bookingId))
                .thenReturn(Optional.of(new BookingEventMapping(bookingId, eventId, java.time.LocalDateTime.now())));
        when(ticketRepository.existsByTicketCode(anyString())).thenReturn(false);
        when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Ticket> tickets = inv.getArgument(0);
            long id = 1L;
            for (Ticket t : tickets) t.setId(id++);
            return tickets;
        });

        int count = ticketService.batchIssueTickets(request);

        assertThat(count).isEqualTo(2);
        verify(bookingServiceFacade, times(1)).getBooking(bookingId);
        verify(ticketRepository, times(1)).saveAll(anyList());
        verify(ticketEventPublisher, times(2)).publishTicketIssued(any(TicketIssuedEvent.class));
    }

    @Test
    void f4_batchIssueTickets_feignThrows_mapsTo404AndDoesNotPublish() {
        Long bookingId = 999L;
        BatchTicketRequestDTO request = new BatchTicketRequestDTO(bookingId, List.of(
                new TicketItemDTO("Alice", "TKT-X", Map.of())));

        when(bookingServiceFacade.getBooking(bookingId)).thenThrow(new RuntimeException("404"));

        assertThatThrownBy(() -> ticketService.batchIssueTickets(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");

        verify(ticketRepository, never()).saveAll(anyList());
        verify(ticketEventPublisher, never()).publishTicketIssued(any());
    }

    // ─── F3: findNearbyTickets — two-stage Feign chain (booking → event) ───────

    @Test
    void f3_findNearbyTickets_chainsBookingAndEventFeign() {
        Long bookingId = 1L;
        Long eventId = 99L;
        double centerLat = 30.0;
        double centerLon = 31.0;

        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setBookingId(bookingId);
        ticket.setEventId(eventId);
        ticket.setStatus(TicketStatus.VALID);
        ticket.setAttendeeName("Alice");

        when(ticketRepository.findByStatus(TicketStatus.VALID)).thenReturn(List.of(ticket));
        when(bookingServiceFacade.getBooking(bookingId)).thenReturn(bookingDto(bookingId, eventId));
        when(eventServiceClient.getEvent(eventId))
                .thenReturn(new EventDTO(eventId, "TestEvent", "UPCOMING", java.time.LocalDateTime.now()));
        when(eventServiceClient.getEventVenueCoords(eventId))
                .thenReturn(new VenueCoordsDTO(centerLat, centerLon));

        List<NearbyTicketDTO> results = ticketService.findNearbyTickets(centerLat, centerLon, 5.0);

        verify(bookingServiceFacade, times(1)).getBooking(bookingId);
        verify(eventServiceClient, times(1)).getEvent(eventId);
        verify(eventServiceClient, times(1)).getEventVenueCoords(eventId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEventName()).isEqualTo("TestEvent");
        assertThat(results.get(0).getDistanceKm()).isEqualTo(0.0);
    }

    // ─── F9: getUnusedUpcomingTickets — event Feign + UPCOMING filter ──────────

    @Test
    void f9_getUnusedUpcomingTickets_callsEventFeignAndFiltersUpcoming() {
        Long upcomingEventId = 1L;
        Long pastEventId = 2L;

        Ticket validForUpcoming = new Ticket();
        validForUpcoming.setId(10L);
        validForUpcoming.setBookingId(100L);
        validForUpcoming.setEventId(upcomingEventId);
        validForUpcoming.setStatus(TicketStatus.VALID);
        validForUpcoming.setAttendeeName("Alice");
        validForUpcoming.setTicketCode("TKT-UP-1");

        Ticket validForPast = new Ticket();
        validForPast.setId(11L);
        validForPast.setBookingId(101L);
        validForPast.setEventId(pastEventId);
        validForPast.setStatus(TicketStatus.VALID);
        validForPast.setAttendeeName("Bob");
        validForPast.setTicketCode("TKT-PA-1");

        when(ticketRepository.findByStatus(TicketStatus.VALID))
                .thenReturn(List.of(validForUpcoming, validForPast));
        when(eventServiceClient.getEvent(upcomingEventId)).thenReturn(
                new EventDTO(upcomingEventId, "UpcomingFest", "UPCOMING",
                        java.time.LocalDateTime.now().plusDays(10)));
        when(eventServiceClient.getEvent(pastEventId)).thenReturn(
                new EventDTO(pastEventId, "PastFest", "COMPLETED",
                        java.time.LocalDateTime.now().minusDays(10)));

        List<UnusedTicketDTO> results = ticketService.getUnusedUpcomingTickets();

        verify(eventServiceClient, times(1)).getEvent(upcomingEventId);
        verify(eventServiceClient, times(1)).getEvent(pastEventId);
        verify(bookingServiceFacade, never()).getBooking(anyLong());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTicketCode()).isEqualTo("TKT-UP-1");
        assertThat(results.get(0).getEventName()).isEqualTo("UpcomingFest");
    }
}
