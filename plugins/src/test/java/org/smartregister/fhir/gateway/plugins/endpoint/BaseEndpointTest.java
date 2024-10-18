package org.smartregister.fhir.gateway.plugins.endpoint;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class BaseEndpointTest {

    @Test
    public void testBaseEndpointInstanceCreation() throws IOException {

        TestBaseEndpoint baseEndpoint = new TestBaseEndpoint();
        Assert.assertNotNull(baseEndpoint);
    }

    static class TestBaseEndpoint extends BaseEndpoint {

        protected TestBaseEndpoint() throws IOException {}
    }
}
