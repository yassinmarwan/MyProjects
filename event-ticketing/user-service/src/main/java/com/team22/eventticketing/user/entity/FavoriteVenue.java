package com.team22.eventticketing.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a venue that a user has marked as a favourite.
 *
 * Table: favorite_venues
 *
 * Relationship: Many FavoriteVenues → One User.
 * FavoriteVenue is the *owner* side — it holds the FK column "user_id".
 * @JsonIgnore on the `user` field prevents circular serialisation loops.
 *
 * JSONB storage: the `metadata` column stores rich venue metadata (parking,
 * accessibility, food options, etc.) as a flexible key-value map using the
 * same JsonbConverter pattern used by the User entity.
 */
@Entity
@Table(name = "favorite_venues")
public class FavoriteVenue {

    // ─── Primary Key ────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Basic Fields ────────────────────────────────────────────────────────────

    /** User-defined label, e.g. "My Go-To", "Weekend Spot". */
    @Column(nullable = false)
    private String label;

    /** Official venue name, e.g. "Cairo Opera House". */
    @Column(nullable = false)
    private String venueName;

    /** Human-readable location string, e.g. "Zamalek, Cairo". */
    @Column(nullable = false)
    private String location;

    /** Venue capacity (nullable — may not always be known). */
    @Column
    private Integer capacity;

    /** Whether this is the user's default / home venue. */
    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean isDefault = false;

    // ─── JSONB Column ─────────────────────────────────────────────────────────────

    /**
     * Flexible key-value metadata stored as JSONB in PostgreSQL.
     *
     * Example keys:
     *   - parkingAvailable    (Boolean)
     *   - wheelchairAccess    (Boolean)
     *   - elevators           (Boolean)
     *   - foodOptions         (List)    e.g. ["café", "restaurant"]
     *   - websiteUrl          (String)
     *   - nearbyTransport     (List)    e.g. ["Metro Line 1", "Bus 72"]
     *   - personalNotes       (String)  free-text user note
     *
     * Hibernate 6 (Spring Boot 3.x) serialises/deserialises the Map natively
     * via Jackson — no custom AttributeConverter needed (Lab 4 pattern).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    // ─── Audit ───────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ─── Relationships ────────────────────────────────────────────────────────────

    /**
     * Owner side of the bidirectional User ↔ FavoriteVenue relationship.
     * The FK column "user_id" lives in the favorite_venues table.
     * @JsonIgnore prevents circular serialisation (FavoriteVenue → User → FavoriteVenue …).
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ─── Constructors ─────────────────────────────────────────────────────────────

    public FavoriteVenue() {}

    public FavoriteVenue(String label, String venueName, String location, User user) {
        this.label = label;
        this.venueName = venueName;
        this.location = location;
        this.user = user;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
