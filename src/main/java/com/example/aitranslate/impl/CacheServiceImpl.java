package com.example.aitranslate.impl;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Redis cache layer.
 *
 * Provides get / set / exists operations for translation results.
 * Values are AES-256 encrypted before storage and decrypted on retrieval.
 * TTL is configured separately for success keys (long) and failed keys (short).
 *
 * Implementation provided by team infrastructure.
 */
@Component
public class CacheServiceImpl {

    public boolean isExistingKey(String cacheKey) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public String getCacheValue(String cacheKey) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public void putSuccessCacheValue(String key, String value) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public void putFailedCacheValue(String key) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }

    public boolean removeByKey(String cacheKey) {
        throw new UnsupportedOperationException("team infra — not included in demo");
    }
}
