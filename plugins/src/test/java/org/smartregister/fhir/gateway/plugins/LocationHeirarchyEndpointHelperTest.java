package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.location.LocationHierarchy;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;

public class LocationHeirarchyEndpointHelperTest {

    private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;
    IGenericClient client;

    @Before
    public void setUp() {
        client = mock(IGenericClient.class, new ReturnsDeepStubs());
        locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper(client);
    }

    @Test
    public void testGetLocationHierarchyNotFound() {
        Bundle bundleLocation = new Bundle();
        Object whenSearchLocation =
                client.search()
                        .forResource(Location.class)
                        .where(any(ICriterion.class))
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenSearchLocation).thenReturn(bundleLocation);
        LocationHierarchy locationHierarchy =
                locationHierarchyEndpointHelper.getLocationHierarchy("12345");
        assertEquals(
                org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND,
                locationHierarchy.getId());
    }

    private Bundle getLocationBundle() {
        Bundle bundleLocation = new Bundle();
        bundleLocation.setId("Location/1234");
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Location location = new Location();
        location.setId("Location/1234");
        Identifier identifier = new Identifier();
        identifier.setValue("location-1234-abcd");
        List<Identifier> identifiers = new ArrayList<Identifier>();
        identifiers.add(identifier);
        location.setIdentifier(identifiers);
        bundleEntryComponent.setResource(location);
        bundleLocation.addEntry(bundleEntryComponent);
        return bundleLocation;
    }
}
