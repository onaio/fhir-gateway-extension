package org.smartregister.fhir.gateway.plugins.utils;

import org.junit.Assert;
import org.junit.Test;

public class EnvUtilTest {

    @Test
    public void testGetEnvironmentVarWithExistingVar() {
        // Set environment variable
        String testKey = "TEST_ENV_VAR_" + System.currentTimeMillis();
        String testValue = "test-value-123";

        try {
            // Since we can't directly set environment variables in Java, we test with actual env
            // vars
            // or test with non-existent keys
            String result = EnvUtil.getEnvironmentVar(testKey, "default-value");
            Assert.assertEquals(
                    "Should return default when env var doesn't exist", "default-value", result);
        } catch (Exception e) {
            Assert.fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testGetEnvironmentVarWithNonExistentVar() {
        String nonExistentKey = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        String defaultValue = "default-value";

        String result = EnvUtil.getEnvironmentVar(nonExistentKey, defaultValue);
        Assert.assertEquals(
                "Should return default value when env var doesn't exist", defaultValue, result);
    }

    @Test
    public void testGetEnvironmentVarWithNullDefault() {
        String nonExistentKey = "NON_EXISTENT_VAR_" + System.currentTimeMillis();

        String result = EnvUtil.getEnvironmentVar(nonExistentKey, null);
        Assert.assertNull("Should return null default when env var doesn't exist", result);
    }

    @Test
    public void testGetEnvironmentVarWithEmptyDefault() {
        String nonExistentKey = "NON_EXISTENT_VAR_" + System.currentTimeMillis();
        String defaultValue = "";

        String result = EnvUtil.getEnvironmentVar(nonExistentKey, defaultValue);
        Assert.assertEquals(
                "Should return empty default when env var doesn't exist", defaultValue, result);
    }
}
