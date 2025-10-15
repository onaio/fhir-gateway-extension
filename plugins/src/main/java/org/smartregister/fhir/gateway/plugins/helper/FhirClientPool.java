package org.smartregister.fhir.gateway.plugins.helper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.Constants;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;

/**
 * Connection pool manager for FHIR clients to improve performance and resource utilization. This
 * class manages a pool of FHIR clients to avoid creating new connections for each request.
 */
public class FhirClientPool {
    private static final Logger logger = LoggerFactory.getLogger(FhirClientPool.class);

    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_POOL_SIZE = 2;
    private static final long ACQUISITION_TIMEOUT_SECONDS = 30;
    private static final long CLIENT_LEASE_TIMEOUT_SECONDS = 300; // 5 minutes

    private final ConcurrentMap<String, PooledFhirClient> clientPool = new ConcurrentHashMap<>();
    private final AtomicInteger activeClients = new AtomicInteger(0);
    private final FhirContext fhirContext;
    private final String baseUrl;

    private static volatile FhirClientPool instance;

    private FhirClientPool(FhirContext fhirContext, String baseUrl) {
        this.fhirContext = fhirContext;
        this.baseUrl = baseUrl;
        // Only initialize pool if we have a valid base URL
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            initializePool();
        }
    }

    /** Get singleton instance of the FHIR client pool */
    public static FhirClientPool getInstance(FhirContext fhirContext) {
        if (instance == null) {
            synchronized (FhirClientPool.class) {
                if (instance == null) {
                    String baseUrl = System.getenv(Constants.PROXY_TO_ENV);
                    // Allow null baseUrl for testing scenarios
                    instance = new FhirClientPool(fhirContext, baseUrl);
                }
            }
        }
        return instance;
    }

    /** Initialize the client pool with minimum number of clients */
    private void initializePool() {
        for (int i = 0; i < MIN_POOL_SIZE; i++) {
            createAndAddClient();
        }
        logger.info("Initialized FHIR client pool with {} clients", MIN_POOL_SIZE);
    }

    /** Get a FHIR client from the pool with bounded wait and timeout */
    public IGenericClient getClient() {
        // If no base URL is configured (e.g., in tests), return a mock client
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return fhirContext.newRestfulGenericClient("http://localhost:8080/fhir");
        }

        // Try to get an available client
        for (PooledFhirClient pooledClient : clientPool.values()) {
            if (pooledClient.tryAcquire()) {
                return pooledClient.getClient();
            }
        }

        // If no available client and we haven't reached max pool size, create a new one
        if (activeClients.get() < MAX_POOL_SIZE) {
            return createAndAddClient().getClient();
        }

        // Wait for an available client with bounded timeout
        return waitForAvailableClientWithTimeout();
    }

    /** Return a client to the pool */
    public void returnClient(IGenericClient client) {
        // Find the pooled client and release it
        for (PooledFhirClient pooledClient : clientPool.values()) {
            if (pooledClient.getClient() == client) {
                pooledClient.release();
                return;
            }
        }
        logger.warn("Attempted to return unknown client to pool");
    }

    /** Create a new client and add it to the pool */
    private PooledFhirClient createAndAddClient() {
        IGenericClient client = fhirContext.newRestfulGenericClient(baseUrl);

        // Add logging interceptor for debugging
        if (logger.isDebugEnabled()) {
            LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
            loggingInterceptor.setLogRequestSummary(true);
            loggingInterceptor.setLogResponseSummary(true);
            client.registerInterceptor(loggingInterceptor);
        }

        PooledFhirClient pooledClient = new PooledFhirClient(client);
        String clientId = "client-" + activeClients.incrementAndGet();
        clientPool.put(clientId, pooledClient);

        logger.debug("Created new FHIR client: {}", clientId);
        return pooledClient;
    }

    /** Wait for an available client with bounded timeout */
    private IGenericClient waitForAvailableClientWithTimeout() {
        long startTime = System.currentTimeMillis();
        long timeoutMs = ACQUISITION_TIMEOUT_SECONDS * 1000;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Try to acquire any available client
            for (PooledFhirClient pooledClient : clientPool.values()) {
                if (pooledClient.tryAcquire()) {
                    return pooledClient.getClient();
                }
            }
            
            // Check for clients that have exceeded their lease timeout
            cleanupExpiredClients();
            
            // Try to acquire again after cleanup
            for (PooledFhirClient pooledClient : clientPool.values()) {
                if (pooledClient.tryAcquire()) {
                    return pooledClient.getClient();
                }
            }
            
            // Wait before retrying
            try {
                Thread.sleep(50); // Short sleep before retry
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for FHIR client", e);
            }
        }
        
        // Timeout reached - throw exception instead of creating temporary client
        throw new RuntimeException(
            String.format("Timeout waiting for FHIR client after %d seconds. Pool stats: %s", 
                ACQUISITION_TIMEOUT_SECONDS, getStats()));
    }

    /** Clean up clients that have exceeded their lease timeout */
    private void cleanupExpiredClients() {
        long currentTime = System.currentTimeMillis();
        long leaseTimeoutMs = CLIENT_LEASE_TIMEOUT_SECONDS * 1000;
        
        clientPool.entrySet().removeIf(entry -> {
            PooledFhirClient pooledClient = entry.getValue();
            if (pooledClient.isInUse() && pooledClient.hasExceededLeaseTimeout(currentTime, leaseTimeoutMs)) {
                logger.warn("Force releasing client {} due to lease timeout", entry.getKey());
                pooledClient.forceRelease();
                return false; // Don't remove from pool, just release it
            }
            return false;
        });
    }

    /** Get pool statistics for monitoring */
    public PoolStats getStats() {
        int totalClients = clientPool.size();
        int availableClients =
                (int) clientPool.values().stream().filter(PooledFhirClient::isAvailable).count();
        int activeClients = this.activeClients.get();

        return new PoolStats(totalClients, availableClients, activeClients);
    }

    /** Pool statistics for monitoring */
    public static class PoolStats {
        private final int totalClients;
        private final int availableClients;
        private final int activeClients;

        public PoolStats(int totalClients, int availableClients, int activeClients) {
            this.totalClients = totalClients;
            this.availableClients = availableClients;
            this.activeClients = activeClients;
        }

        public int getTotalClients() {
            return totalClients;
        }

        public int getAvailableClients() {
            return availableClients;
        }

        public int getActiveClients() {
            return activeClients;
        }

        @Override
        public String toString() {
            return String.format(
                    "PoolStats{total=%d, available=%d, active=%d}",
                    totalClients, availableClients, activeClients);
        }
    }

    /** Wrapper for pooled FHIR client with acquisition tracking and lease timeout */
    private static class PooledFhirClient {
        private final IGenericClient client;
        private volatile boolean inUse = false;
        private volatile long acquiredAt = 0;

        public PooledFhirClient(IGenericClient client) {
            this.client = client;
        }

        public IGenericClient getClient() {
            return client;
        }

        public synchronized boolean tryAcquire() {
            if (!inUse) {
                inUse = true;
                acquiredAt = System.currentTimeMillis();
                return true;
            }
            return false;
        }

        public synchronized void release() {
            inUse = false;
            acquiredAt = 0;
        }

        public synchronized void forceRelease() {
            inUse = false;
            acquiredAt = 0;
        }

        public boolean isAvailable() {
            return !inUse;
        }

        public boolean isInUse() {
            return inUse;
        }

        public boolean hasExceededLeaseTimeout(long currentTime, long leaseTimeoutMs) {
            return inUse && (currentTime - acquiredAt) > leaseTimeoutMs;
        }
    }
}
