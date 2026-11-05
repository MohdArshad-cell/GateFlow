package com.gateflow.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Tiering Service for API Key and User subscription tiers.
 * Resolves rate limits dynamically based on subscription tier (FREE, PRO, ENTERPRISE).
 */
@Service
public class TierService {

    @Getter
    @RequiredArgsConstructor
    public enum Tier {
        FREE(10, 60),          // 10 requests per minute
        PRO(100, 60),          // 100 requests per minute
        ENTERPRISE(1000, 60);  // 1000 requests per minute

        private final int limit;
        private final int windowSeconds;
    }

    // Simulated API Key to Tier mapping (in production, loaded from DB / Redis cache)
    private final Map<String, Tier> apiKeyTierCache = new ConcurrentHashMap<>(Map.of(
            "key_free_123", Tier.FREE,
            "key_pro_456", Tier.PRO,
            "key_enterprise_789", Tier.ENTERPRISE
    ));

    /**
     * Resolves the subscription tier for an API key. Defaults to FREE.
     */
    public Tier resolveTier(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return Tier.FREE;
        }
        return apiKeyTierCache.getOrDefault(apiKey, Tier.FREE);
    }

    /**
     * Registers or updates an API Key's tier dynamically.
     */
    public void registerApiKey(String apiKey, Tier tier) {
        apiKeyTierCache.put(apiKey, tier);
    }
}

