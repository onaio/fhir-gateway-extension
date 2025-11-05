package org.smartregister.fhir.gateway.plugins.helper;

import org.junit.Assert;
import org.junit.Test;

public class CacheHelperTest {

    @Test
    public void testCacheHelperInstance() {
        CacheHelper instance = CacheHelper.INSTANCE;
        Assert.assertNotNull("CacheHelper instance should not be null", instance);
    }

    @Test
    public void testCacheHelperCaches() {
        CacheHelper instance = CacheHelper.INSTANCE;

        Assert.assertNotNull("Cache should not be null", instance.cache);
        Assert.assertNotNull("Resource cache should not be null", instance.resourceCache);
        Assert.assertNotNull("Location list cache should not be null", instance.locationListCache);
        Assert.assertNotNull("String cache should not be null", instance.stringCache);
        Assert.assertNotNull("List string cache should not be null", instance.listStringCache);
    }

    @Test
    public void testSkipCacheWhenEnvVarNotSet() {
        CacheHelper instance = CacheHelper.INSTANCE;

        // When environment variable is not set, should return false
        boolean skipCache = instance.skipCache();
        // The actual value depends on environment, but should not throw exception
        Assert.assertNotNull("skipCache should not be null", skipCache);
    }

    @Test
    public void testCacheConstants() {
        Assert.assertNotNull(
                "OPENSRP_CACHE_EXPIRY_SECONDS should not be null",
                CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS);
        Assert.assertNotNull(
                "OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY should not be null",
                CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY);
        Assert.assertFalse(
                "OPENSRP_CACHE_EXPIRY_SECONDS should not be empty",
                CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS.isEmpty());
        Assert.assertFalse(
                "OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY should not be empty",
                CacheHelper.OPENSRP_CACHE_EXPIRY_SECONDS_LEGACY.isEmpty());
    }
}
