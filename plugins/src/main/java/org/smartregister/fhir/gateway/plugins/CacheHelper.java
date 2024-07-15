package org.smartregister.fhir.gateway.plugins;

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
    Cache<String, Map<String, List<String>>> cache;

    Cache<String, DomainResource> resourceCache;

    Cache<String, List<Location>> locationListCache;

    Cache<String, String> stringCache;

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
    }

    private int getCacheExpiryDurationInSeconds() {
        String duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS);
        if (StringUtils.isNotBlank(duration)) {
            return Integer.parseInt(duration);
        }
        return 60;
    }

    public boolean skipCache() {
        String duration = System.getenv(CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS);
        return StringUtils.isNotBlank(duration) && "0".equals(duration.trim());
    }

    public static final String OPENSRP_CACHE_EXPIRY_SECONDS = "openrsp_cache_timeout_seconds";
    private static final int DEFAULT_CACHE_SIZE = 1_000;
}
