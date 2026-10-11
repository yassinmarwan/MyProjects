package com.team22.eventticketing.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an application user.
 *
 * Table: users
 *
 * Enum storage: both Role and UserStatus are persisted as VARCHAR strings via
 * @Enumerated(EnumType.STRING). This avoids ordinal-based breakage when enum
 * values are reordered, and makes the stored value human-readable in the DB.
 *
 * JSONB storage: the `preferences` column uses a custom JPA AttributeConverter
 * (JsonbConverter) that serialises/deserialises a Map<String, Object> to a
 * PostgreSQL jsonb column. The column definition is declared explicitly so that
 * Hibernate creates it with the correct type during schema generation.
 *
 * Relationship: One User → Many FavoriteVenues.
 * User is the *inverse* (mappedBy) side; FavoriteVenue owns the FK.
 * @JsonIgnore prevents infinite recursion when the list is serialised.
 */
@Entity
@Table(name = "users")
public class User {

    // ─── Primary Key ────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Basic Fields ────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String phone;

    // ─── Enum Columns ────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Default value at the application layer is ACTIVE.
     * The DB-level default is set via columnDefinition so schema generation
     * also reflects the default without requiring a NOT NULL migration.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
    private UserStatus status = UserStatus.ACTIVE;

    // ─── JSONB Column ─────────────────────────────────────────────────────────────

    /**
     * Flexible key-value preferences stored as JSONB in PostgreSQL.
     *
     * Example keys:
     *   - language            (String)  e.g. "en"
     *   - notifyByEmail       (Boolean) notification preference
     *   - notifyBySms         (Boolean) notification preference
     *   - favoriteCategories  (List)    e.g. ["music", "sports"]
     *   - preferredTicketType (String)  "VIP" or "standard"
     *   - timezone            (String)  e.g. "Africa/Cairo"
     *
     * Hibernate 6 (Spring Boot 3.x) serialises/deserialises the Map natively
     * via Jackson — no custom AttributeConverter needed (Lab 4 pattern).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> preferences = new HashMap<>();

    // ─── Audit ───────────────────────────────────────────────────────────────────

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ─── Relationships ────────────────────────────────────────────────────────────

    /**
     * Inverse side of the User ↔ FavoriteVenue bidirectional relationship.
     * FavoriteVenue is the owner side (it holds the FK column "user_id").
     * cascade = ALL propagates persistence / removal to child venues.
     * orphanRemoval removes a FavoriteVenue row when it is unlinked from this list.
     * @JsonIgnore prevents circular serialisation (User → FavoriteVenue → User …).
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FavoriteVenue> favoriteVenues = new ArrayList<>();

    // ─── Constructors ─────────────────────────────────────────────────────────────

    public User() {}

    public User(String name, String email, String password, String phone, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.role = role;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<FavoriteVenue> getFavoriteVenues() { return favoriteVenues; }
    public void setFavoriteVenues(List<FavoriteVenue> favoriteVenues) { this.favoriteVenues = favoriteVenues; }
}
