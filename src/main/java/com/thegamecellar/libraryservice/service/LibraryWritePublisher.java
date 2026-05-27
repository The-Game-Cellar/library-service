package com.thegamecellar.libraryservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// Publishes a "userId changed" signal to the Redis "library-write" channel after any write
// that should invalidate the user's recommendation pool. Rec-service subscriber picks up the
// userId and enqueues a full pool recompute. Best-effort: Redis outages do not break library
// writes (worker's hourly TTL scan eventually catches up).
@Slf4j
@Service
public class LibraryWritePublisher {

    public static final String CHANNEL = "library-write";

    // Optional so library-service still boots when Redis is down (auto-config registers the
    // template either way; the actual publish call swallows the connect-failure exception).
    @Autowired(required = false)
    private StringRedisTemplate redis;

    public void publish(String userId) {
        if (userId == null || userId.isBlank()) return;
        if (redis == null) return;
        try {
            redis.convertAndSend(CHANNEL, userId);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish library-write event for {}: {}", userId, ex.getClass().getSimpleName());
        }
    }
}
