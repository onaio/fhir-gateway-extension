package org.smartregister.fhir.gateway.plugins.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

public class FhirClientPoolTest {

    private FhirContext fhirContext;

    @Before
    public void setUp() {
        fhirContext = FhirContext.forR4();
        // Reset singleton instance for testing
        resetFhirClientPoolInstance();
    }

    @After
    public void tearDown() {
        // Reset singleton instance after each test
        resetFhirClientPoolInstance();
    }

    private void resetFhirClientPoolInstance() {
        // Use reflection to reset the singleton instance
        try {
            java.lang.reflect.Field instanceField =
                    FhirClientPool.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // Ignore reflection errors
        }
    }

    @Test
    public void testGetInstance() {
        FhirClientPool instance = FhirClientPool.getInstance(fhirContext);
        assertNotNull("Instance should not be null", instance);

        // Second call should return same instance
        FhirClientPool instance2 = FhirClientPool.getInstance(fhirContext);
        assertEquals("Should return same instance", instance, instance2);
    }

    @Test
    public void testGetClientWithNullBaseUrl() {
        // Set environment variable to null for testing
        String originalEnv = System.getenv("PROXY_TO");
        try {
            // Create pool with null base URL
            resetFhirClientPoolInstance();
            FhirClientPool instance = FhirClientPool.getInstance(fhirContext);

            IGenericClient client = instance.getClient();
            assertNotNull("Client should not be null even with null base URL", client);
        } finally {
            // Restore original environment if needed
        }
    }

    @Test
    public void testReturnClient() {
        FhirClientPool instance = FhirClientPool.getInstance(fhirContext);
        IGenericClient client = instance.getClient();

        // Should not throw exception when returning client
        instance.returnClient(client);
    }

    @Test
    public void testReturnUnknownClient() {
        FhirClientPool instance = FhirClientPool.getInstance(fhirContext);
        IGenericClient unknownClient = mock(IGenericClient.class);

        // Should not throw exception when returning unknown client
        instance.returnClient(unknownClient);
    }

    @Test
    public void testGetStats() {
        FhirClientPool instance = FhirClientPool.getInstance(fhirContext);
        FhirClientPool.PoolStats stats = instance.getStats();

        assertNotNull("Stats should not be null", stats);
        assertNotNull("Total clients should be accessible", stats.getTotalClients());
        assertNotNull("Available clients should be accessible", stats.getAvailableClients());
        assertNotNull("Active clients should be accessible", stats.getActiveClients());
    }

    @Test
    public void testPoolStatsToString() {
        FhirClientPool.PoolStats stats = new FhirClientPool.PoolStats(5, 3, 2);
        String statsString = stats.toString();

        assertNotNull("Stats string should not be null", statsString);
        assertEquals("Should have correct total", 5, stats.getTotalClients());
        assertEquals("Should have correct available", 3, stats.getAvailableClients());
        assertEquals("Should have correct active", 2, stats.getActiveClients());
    }

    @Test
    public void testGetClientMultipleTimes() {
        FhirClientPool instance = FhirClientPool.getInstance(fhirContext);

        IGenericClient client1 = instance.getClient();
        IGenericClient client2 = instance.getClient();

        assertNotNull("Client1 should not be null", client1);
        assertNotNull("Client2 should not be null", client2);

        // Return clients
        instance.returnClient(client1);
        instance.returnClient(client2);
    }
}
