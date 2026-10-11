package com.team22.eventticketing.user.repository;

import com.team22.eventticketing.user.entity.FavoriteVenue;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface FavoriteVenueRepository extends JpaRepository<FavoriteVenue, Long> {

    List<FavoriteVenue> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE FavoriteVenue f SET f.isDefault = false WHERE f.user.id = :userId")
    void clearDefaultsByUserId(@Param("userId") Long userId);
}
