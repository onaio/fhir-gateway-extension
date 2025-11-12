package org.smartregister.fhir.gateway.plugins.helper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.hl7.fhir.r4.model.Location;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class StreamingResponseHelperTest {

    private StreamingResponseHelper streamingResponseHelper;
    private IParser fhirJsonParser;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() {
        FhirContext fhirContext = FhirContext.forR4();
        fhirJsonParser = fhirContext.newJsonParser().setPrettyPrint(false);
        streamingResponseHelper = new StreamingResponseHelper(fhirJsonParser);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    public void testShouldUseStreaming() {
        Assert.assertTrue(
                "Should use streaming for large datasets",
                StreamingResponseHelper.shouldUseStreaming(1001, 1000));
        Assert.assertFalse(
                "Should not use streaming for small datasets",
                StreamingResponseHelper.shouldUseStreaming(999, 1000));
        Assert.assertFalse(
                "Should not use streaming when equal to threshold",
                StreamingResponseHelper.shouldUseStreaming(1000, 1000));
    }

    @Test
    public void testGetOptimalChunkSize() {
        Assert.assertEquals(
                "Small datasets should return total size",
                50,
                StreamingResponseHelper.getOptimalChunkSize(50));
        Assert.assertEquals(
                "Medium datasets should return DEFAULT_CHUNK_SIZE",
                100,
                StreamingResponseHelper.getOptimalChunkSize(500));
        // For 10000, it should return Math.min(MAX_CHUNK_SIZE, totalSize / 10) = Math.min(500,
        // 1000) = 500
        Assert.assertEquals(
                "Large datasets should return MAX_CHUNK_SIZE",
                500,
                StreamingResponseHelper.getOptimalChunkSize(10000));
    }

    @Test
    public void testStreamLocationBundle() throws IOException {
        List<Location> locations = createTestLocations(5);
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        jakarta.servlet.ServletOutputStream servletOutputStream =
                new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        // No-op for testing
                    }
                };
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://test:8080/LocationHierarchy"));
        when(request.getQueryString()).thenReturn("page=1&size=10");

        streamingResponseHelper.streamLocationBundle(request, response, locations, 1, 10, 5);

        String result = outputStream.toString();
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should contain resourceType", result.contains("resourceType"));
        Assert.assertTrue("Result should contain Bundle", result.contains("Bundle"));
        Assert.assertTrue("Result should contain total", result.contains("\"total\": 5"));
    }

    @Test
    public void testStreamLocationBundleWithEmptyList() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        jakarta.servlet.ServletOutputStream servletOutputStream =
                new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        // No-op for testing
                    }
                };
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://test:8080/LocationHierarchy"));
        when(request.getQueryString()).thenReturn(null);

        streamingResponseHelper.streamLocationBundle(
                request, response, Collections.emptyList(), 1, 10, 0);

        String result = outputStream.toString();
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should contain total 0", result.contains("\"total\": 0"));
    }

    @Test
    public void testStreamLocationBundleWithChunking() throws IOException {
        BiFunction<Integer, Integer, List<Location>> locationProvider =
                (offset, limit) -> {
                    List<Location> locations = new ArrayList<>();
                    for (int i = offset; i < Math.min(offset + limit, 5); i++) {
                        Location location = new Location();
                        location.setId("loc-" + i);
                        locations.add(location);
                    }
                    return locations;
                };

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        jakarta.servlet.ServletOutputStream servletOutputStream =
                new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        // No-op for testing
                    }
                };
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://test:8080/LocationHierarchy"));
        when(request.getQueryString()).thenReturn("page=1&size=10");
        Map<String, String[]> params = new HashMap<>();
        params.put("param1", new String[] {"value1"});
        when(request.getParameterMap()).thenReturn(params);

        streamingResponseHelper.streamLocationBundleWithChunking(
                request, response, locationProvider, 5, 1, 10);

        String result = outputStream.toString();
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should contain resourceType", result.contains("resourceType"));
        Assert.assertTrue("Result should contain Bundle", result.contains("Bundle"));
    }

    @Test
    public void testStreamLocationBundleWithChunkingEmptyResult() throws IOException {
        BiFunction<Integer, Integer, List<Location>> locationProvider =
                (offset, limit) -> Collections.emptyList();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        jakarta.servlet.ServletOutputStream servletOutputStream =
                new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        // No-op for testing
                    }
                };
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://test:8080/LocationHierarchy"));
        when(request.getQueryString()).thenReturn(null);
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        streamingResponseHelper.streamLocationBundleWithChunking(
                request, response, locationProvider, 0, 1, 10);

        String result = outputStream.toString();
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should contain total 0", result.contains("\"total\": 0"));
    }

    @Test
    public void testStreamLocationBundleWithPagination() throws IOException {
        List<Location> locations = createTestLocations(25);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        jakarta.servlet.ServletOutputStream servletOutputStream =
                new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener listener) {
                        // No-op for testing
                    }
                };
        when(response.getOutputStream()).thenReturn(servletOutputStream);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://test:8080/LocationHierarchy"));
        when(request.getQueryString()).thenReturn("page=2&size=10");
        Map<String, String[]> params = new HashMap<>();
        when(request.getParameterMap()).thenReturn(params);

        streamingResponseHelper.streamLocationBundle(request, response, locations, 2, 10, 25);

        String result = outputStream.toString();
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should contain pagination links", result.contains("link"));
    }

    private List<Location> createTestLocations(int count) {
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location location = new Location();
            location.setId("loc-" + i);
            location.setName("Location " + i);
            locations.add(location);
        }
        return locations;
    }
}
