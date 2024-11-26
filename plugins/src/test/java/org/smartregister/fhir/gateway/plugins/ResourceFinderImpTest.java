package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Before;
import org.junit.Test;

import com.google.fhir.gateway.interfaces.RequestDetailsReader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;

public class ResourceFinderImpTest {

    private FhirContext fhirContextMock;
    private IParser jsonParserMock;
    private RequestDetailsReader requestDetailsReaderMock;
    private ResourceFinderImp resourceFinder;

    @Before
    public void setUp() throws Exception {
        Field instanceField = ResourceFinderImp.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        fhirContextMock = mock(FhirContext.class);
        jsonParserMock = mock(IParser.class);
        requestDetailsReaderMock = mock(RequestDetailsReader.class);
        resourceFinder = ResourceFinderImp.getInstance(fhirContextMock);
        when(fhirContextMock.newJsonParser()).thenReturn(jsonParserMock);
    }

    @Test
    public void testCreateResourceFromRequestWithValidJson() {
        String jsonString = "{\"resourceType\":\"Bundle\"}";
        byte[] requestContentBytes = jsonString.getBytes(StandardCharsets.UTF_8);

        when(requestDetailsReaderMock.loadRequestContents()).thenReturn(requestContentBytes);
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);
        Bundle expectedBundle = new Bundle();
        when(jsonParserMock.parseResource(jsonString)).thenReturn(expectedBundle);

        IBaseResource resource = resourceFinder.createResourceFromRequest(requestDetailsReaderMock);

        assertNotNull(resource);
        assertTrue(resource instanceof Bundle);
        assertEquals(expectedBundle, resource);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateResourceFromRequestWithInvalidJson() {
        String invalidJsonString = "{invalid json}";
        byte[] requestContentBytes = invalidJsonString.getBytes(StandardCharsets.UTF_8);
        when(requestDetailsReaderMock.loadRequestContents()).thenReturn(requestContentBytes);
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);
        when(jsonParserMock.parseResource(invalidJsonString))
                .thenThrow(new IllegalArgumentException("Invalid JSON"));
        resourceFinder.createResourceFromRequest(requestDetailsReaderMock);
    }

    @Test
    public void testFindResourcesInBundleWithValidTransactionBundle() {
        String bundleJson =
                "{\"resourceType\": \"Bundle\", \"type\": \"transaction\", \"entry\": []}";
        when(requestDetailsReaderMock.loadRequestContents())
                .thenReturn(bundleJson.getBytes(StandardCharsets.UTF_8));
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        when(jsonParserMock.parseResource(bundleJson)).thenReturn(bundle);
        List<BundleResources> result =
                resourceFinder.findResourcesInBundle(requestDetailsReaderMock);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void testFindResourcesInBundleWithNonTransactionBundle() {
        String bundleJson = "{\"resourceType\": \"Bundle\", \"type\": \"document\", \"entry\": []}";
        when(requestDetailsReaderMock.loadRequestContents())
                .thenReturn(bundleJson.getBytes(StandardCharsets.UTF_8));
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.DOCUMENT);
        when(jsonParserMock.parseResource(bundleJson)).thenReturn(bundle);
        resourceFinder.findResourcesInBundle(requestDetailsReaderMock);
    }

    @Test(expected = RuntimeException.class)
    public void testFindResourcesInBundleWithNonBundleResource() {
        String nonBundleJson = "{\"resourceType\": \"Patient\", \"id\": \"123\"}";
        when(requestDetailsReaderMock.loadRequestContents())
                .thenReturn(nonBundleJson.getBytes(StandardCharsets.UTF_8));
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);

        Resource nonBundleResource = mock(Resource.class);
        when(jsonParserMock.parseResource(nonBundleJson)).thenReturn(nonBundleResource);
        resourceFinder.findResourcesInBundle(requestDetailsReaderMock);
    }

    @Test(expected = RuntimeException.class)
    public void testFindResourcesInBundleWithBundleMissingResourceInEntry() {
        String bundleJson =
                "{\"resourceType\": \"Bundle\", \"type\": \"transaction\", \"entry\":"
                        + " [{\"request\": {\"method\": \"POST\"}}]}";
        when(requestDetailsReaderMock.loadRequestContents())
                .thenReturn(bundleJson.getBytes(StandardCharsets.UTF_8));
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.getRequest().setMethod(Bundle.HTTPVerb.POST);
        bundle.addEntry(entry);
        when(jsonParserMock.parseResource(bundleJson)).thenReturn(bundle);
        resourceFinder.findResourcesInBundle(requestDetailsReaderMock);
    }

    @Test
    public void testFindResourcesInBundleWithValidEntries() {
        String bundleJson =
                "{\"resourceType\": \"Bundle\", \"type\": \"transaction\", \"entry\":"
                        + " [{\"request\": {\"method\": \"POST\"}, \"resource\": {\"resourceType\":"
                        + " \"Patient\", \"id\": \"123\"}}]}";
        when(requestDetailsReaderMock.loadRequestContents())
                .thenReturn(bundleJson.getBytes(StandardCharsets.UTF_8));
        when(requestDetailsReaderMock.getCharset()).thenReturn(StandardCharsets.UTF_8);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.getRequest().setMethod(Bundle.HTTPVerb.POST);
        Resource resource = mock(Resource.class);
        entry.setResource(resource);
        bundle.addEntry(entry);
        when(jsonParserMock.parseResource(bundleJson)).thenReturn(bundle);
        List<BundleResources> result =
                resourceFinder.findResourcesInBundle(requestDetailsReaderMock);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(RequestTypeEnum.POST, result.get(0).getRequestType());
        assertEquals(resource, result.get(0).getResource());
    }
}
