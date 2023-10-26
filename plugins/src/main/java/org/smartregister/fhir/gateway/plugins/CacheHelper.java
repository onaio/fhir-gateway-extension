package org.smartregister.fhir.gateway.plugins;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public enum CacheHelper {
    INSTANCE;
    Cache<String, Map<String, List<String>>> cache;

    CacheHelper() {
        cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(getCacheExpiryDurationInSeconds(), TimeUnit.SECONDS)
                        .maximumSize(1_000)
                        .build();
    }

    private int getCacheExpiryDurationInSeconds() {
        String duration = System.getenv(OPENSRP_CACHE_EXPIRY_SECONDS);
        if (StringUtils.isNotBlank(duration)) {
            return Integer.parseInt(duration);
        }
        return 60;
    }

    public static final String OPENSRP_CACHE_EXPIRY_SECONDS = "openrsp_cache_timeout_seconds";
}
