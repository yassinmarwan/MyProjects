package com.team22.eventticketing.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionService.class);
    private static final String PREFIX = "user-service::";

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheEvictionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Delete a single entity-detail key, e.g. user-service::user::42 */
    public void evictDetail(String entity, long id) {
        deleteKey(PREFIX + entity + "::" + id);
    }

    /**
     * Wildcard-delete all keys matching user-service::{pattern}::*
     * Pass the feature ID as pattern, e.g. "S1-F3" or "S1-F*"
     */
    public void evictPattern(String pattern) {
        String fullPattern = PREFIX + pattern + "::*";
        try {
            ScanOptions opts = ScanOptions.scanOptions().match(fullPattern).count(100).build();
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(opts)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} keys matching {}", keys.size(), fullPattern);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed for pattern {}: {}", fullPattern, e.getMessage());
        }
    }

    /** Evict entity detail key + all listed feature-result patterns */
    public void evictEntityAndFeatures(String entity, long id, String... featurePatterns) {
        evictDetail(entity, id);
        for (String p : featurePatterns) evictPattern(p);
    }

    private void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Cache eviction failed for key {}: {}", key, e.getMessage());
        }
    }
}
