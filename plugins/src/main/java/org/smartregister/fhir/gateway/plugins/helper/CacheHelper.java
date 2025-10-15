package org.smartregister.fhir.gateway.plugins.helper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Location;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public enum CacheHelper {
    INSTANCE;
    public final Cache<String, Map<String, List<String>>> cache;
    final Cache<String, DomainResource> resourceCache;
    final Cache<String, List<Location>> locationListCache;
    final Cache<String, String> stringCache;
    final Cache<String, List<String>> listStringCache;

    CacheHelper() {
        cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(DEFAULT_CACHE_SIZE)
                        .build();
        resourceCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(DEFAULT_CACHE_SIZE)
                        .build();
        locationListCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(DEFAULT_CACHE_SIZE)
                        .build();
        stringCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(DEFAULT_CACHE_SIZE)
                        .build();
        listStringCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(DEFAULT_CACHE_SIZE)
                        .build();
    }

    private int getCacheExpiryDurationInSeconds() {
        // Check new environment variable first (preferred)
        String duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS);
        if (StringUtils.isNotBlank(duration)) {
            return Integer.parseInt(duration);
        }
        
        // Fallback to legacy environment variable for backward compatibility
        duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY);
        if (StringUtils.isNotBlank(duration)) {
            return Integer.parseInt(duration);
        }
        
        return 300; // Increased from 60 to 300 seconds (5 minutes) for better performance
    }

    public boolean skipCache() {
        // Check new environment variable first (preferred)
        String duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS);
        if (StringUtils.isNotBlank(duration)) {
            return "0".equals(duration.trim());
        }
        
        // Fallback to legacy environment variable for backward compatibility
        duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY);
        if (StringUtils.isNotBlank(duration)) {
            return "0".equals(duration.trim());
        }
        
        return false; // Default to not skipping cache
    }

    public static final String OPENSRP_CACHE_EXPIRY_SECONDS =
            "opensrp_cache_timeout_seconds"; // Fixed typo
    public static final String OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY =
            "openrsp_cache_timeout_seconds"; // Legacy name for backward compatibility
    private static final int DEFAULT_CACHE_SIZE =
            5_000; // Increased from 1,000 to 5,000 for better performance
}
