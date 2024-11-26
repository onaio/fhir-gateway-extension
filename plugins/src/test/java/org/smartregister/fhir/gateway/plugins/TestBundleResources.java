package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Test;

import ca.uhn.fhir.rest.api.RequestTypeEnum;

public class TestBundleResources {

    @Test
    public void testConstructorAndGetters() {
        RequestTypeEnum requestType = RequestTypeEnum.POST;
        IBaseResource resource = mock(IBaseResource.class);
        BundleResources bundleResources = new BundleResources(requestType, resource);

        assertEquals(requestType, bundleResources.getRequestType());
        assertEquals(resource, bundleResources.getResource());
    }

    @Test
    public void testSetters() {
        BundleResources bundleResources = new BundleResources(null, null);
        RequestTypeEnum requestType = RequestTypeEnum.GET;
        IBaseResource resource = mock(IBaseResource.class);
        bundleResources.setRequestType(requestType);
        bundleResources.setResource(resource);
        assertEquals(requestType, bundleResources.getRequestType());
        assertEquals(resource, bundleResources.getResource());
    }
}
