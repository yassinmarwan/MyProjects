package com.team22.eventticketing.user.dto;

import com.team22.eventticketing.user.entity.FavoriteVenue;

import java.util.List;
import java.util.Map;

public class UserProfileDTO {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<VenueDTO> favoriteVenues;
    private int totalFavoriteVenues;

    // ─── Nested DTO ───────────────────────────────────────────────────────────────

    public static class VenueDTO {
        private String label;
        private String venueName;
        private String location;
        private Integer capacity;
        private Boolean isDefault;
        private Map<String, Object> metadata;

        public static VenueDTO from(FavoriteVenue venue) {
            VenueDTO dto = new VenueDTO();
            dto.label      = venue.getLabel();
            dto.venueName  = venue.getVenueName();
            dto.location   = venue.getLocation();
            dto.capacity   = venue.getCapacity();
            dto.isDefault  = venue.getIsDefault();
            dto.metadata   = venue.getMetadata();
            return dto;
        }

        public String getLabel()               { return label; }
        public String getVenueName()           { return venueName; }
        public String getLocation()            { return location; }
        public Integer getCapacity()           { return capacity; }
        public Boolean getIsDefault()          { return isDefault; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    // ─── Factory method ───────────────────────────────────────────────────────────

    public static UserProfileDTO from(com.team22.eventticketing.user.entity.User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.userId    = user.getId();
        dto.name      = user.getName();
        dto.email     = user.getEmail();
        dto.phone     = user.getPhone();
        dto.preferences        = user.getPreferences();
        dto.favoriteVenues     = user.getFavoriteVenues()
                .stream()
                .map(VenueDTO::from)
                .toList();
        dto.totalFavoriteVenues = dto.favoriteVenues.size();
        return dto;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────────

    public Long getUserId()                        { return userId; }
    public String getName()                        { return name; }
    public String getEmail()                       { return email; }
    public String getPhone()                       { return phone; }
    public Map<String, Object> getPreferences()    { return preferences; }
    public List<VenueDTO> getFavoriteVenues()      { return favoriteVenues; }
    public int getTotalFavoriteVenues()            { return totalFavoriteVenues; }
}