package com.team22.eventticketing.sales.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.time.LocalDateTime;

/**
 * Join entity linking TicketSale and Promotion in a many-to-many relationship
 * with extra columns (discountApplied, appliedAt).
 *
 * Table: sale_promotions
 *
 * This is the OWNER side of both relationships:
 *   - Holds FK ticket_sale_id → ticket_sales.id
 *   - Holds FK promotion_id   → promotions.id
 *
 * A ticket sale can have multiple promotions applied,
 * and a promotion can be used across multiple ticket sales.
 */
@Entity
@Table(name = "sale_promotions")
public class SalePromotion {

    // ─── Primary Key ──────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Extra Columns ────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private Double discountApplied;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    // ─── Relationships (Owner Side) ───────────────────────────────────────────────

    /**
     * Owner side: holds FK ticket_sale_id.
     * Many SalePromotions belong to One TicketSale.
     * LAZY fetch avoids loading the full TicketSale unless needed.
     */
    @JsonBackReference
    //@JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_sale_id", nullable = false)
    private TicketSale ticketSale;

    /**
     * Owner side: holds FK promotion_id.
     * Many SalePromotions belong to One Promotion.
     * LAZY fetch avoids loading the full Promotion unless needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    // ─── Constructors ─────────────────────────────────────────────────────────────

    public SalePromotion() {}

    public SalePromotion(TicketSale ticketSale, Promotion promotion,
                         Double discountApplied, LocalDateTime appliedAt) {
        this.ticketSale = ticketSale;
        this.promotion = promotion;
        this.discountApplied = discountApplied;
        this.appliedAt = appliedAt;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(Double discountApplied) { this.discountApplied = discountApplied; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public TicketSale getTicketSale() { return ticketSale; }
    public void setTicketSale(TicketSale ticketSale) { this.ticketSale = ticketSale; }

    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }
}
