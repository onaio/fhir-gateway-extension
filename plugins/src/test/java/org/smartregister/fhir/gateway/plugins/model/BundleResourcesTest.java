package org.smartregister.fhir.gateway.plugins.model;

import org.hl7.fhir.r4.model.Patient;
import org.junit.Assert;
import org.junit.Test;

import ca.uhn.fhir.rest.api.RequestTypeEnum;

public class BundleResourcesTest {

    @Test
    public void testBundleResourcesConstructor() {
        RequestTypeEnum requestType = RequestTypeEnum.GET;
        Patient patient = new Patient();
        patient.setId("test-id");

        BundleResources bundleResources = new BundleResources(requestType, patient);

        Assert.assertNotNull("BundleResources should not be null", bundleResources);
        Assert.assertEquals(
                "Request type should match", requestType, bundleResources.getRequestType());
        Assert.assertEquals("Resource should match", patient, bundleResources.getResource());
    }

    @Test
    public void testBundleResourcesSetters() {
        BundleResources bundleResources = new BundleResources(RequestTypeEnum.GET, new Patient());

        RequestTypeEnum newRequestType = RequestTypeEnum.POST;
        Patient newPatient = new Patient();
        newPatient.setId("new-id");

        bundleResources.setRequestType(newRequestType);
        bundleResources.setResource(newPatient);

        Assert.assertEquals(
                "Request type should be updated", newRequestType, bundleResources.getRequestType());
        Assert.assertEquals(
                "Resource should be updated", newPatient, bundleResources.getResource());
    }

    @Test
    public void testBundleResourcesWithNullResource() {
        BundleResources bundleResources = new BundleResources(RequestTypeEnum.GET, null);

        Assert.assertNotNull("BundleResources should not be null", bundleResources);
        Assert.assertNull("Resource should be null", bundleResources.getResource());
        Assert.assertEquals(
                "Request type should match", RequestTypeEnum.GET, bundleResources.getRequestType());
    }
}
