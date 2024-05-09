package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.practitioner.PractitionerDetails;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;

public class PractitionerDetailsEndpointHelperTest {

    private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;
    IGenericClient client;

    @Before
    public void setUp() {
        client = mock(IGenericClient.class, new ReturnsDeepStubs());
        practitionerDetailsEndpointHelper = new PractitionerDetailsEndpointHelper(client);
    }

    @Test
    public void testGetPractitonerDetailsByKeycloakIdNotFound() {

        Bundle bundlePractitioner = new Bundle();
        Object whenObj =
                client.search()
                        .forResource(eq(Practitioner.class))
                        .where(any(ICriterion.class))
                        .returnBundle(any())
                        .execute();

        when(whenObj).thenReturn(bundlePractitioner);
        PractitionerDetails practitionerDetails =
                practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId("111");
        assertEquals(
                org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND,
                practitionerDetails.getId());
    }

    @Test
    public void testGetPractitonerDetailsByKeycloakIdReturnsCorrectPractitioner() {

        Object whenPractitionerSearch =
                client.search()
                        .forResource(eq(Practitioner.class))
                        .where(any(ICriterion.class))
                        .returnBundle(any())
                        .execute();
        when(whenPractitionerSearch).thenReturn(getPractitionerBundle());
        PractitionerDetails practitionerDetails =
                practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(
                        "keycloak-uuid-1234-1234");
        assertEquals(
                "keycloak-uuid-1234-1234",
                practitionerDetails
                        .getFhirPractitionerDetails()
                        .getPractitioners()
                        .get(0)
                        .getIdentifier()
                        .get(0)
                        .getValue());
        assertEquals(
                "Practitioner/1234",
                practitionerDetails.getFhirPractitionerDetails().getPractitioners().get(0).getId());
    }

    @Test
    public void testGetAttributedLocationsWithNoParentChildrenReturnsLocationId() {

        String locationHierarchyNoParentChildren =
                "{\n"
                        + "  \"resourceType\": \"LocationHierarchy\",\n"
                        + "  \"id\": \"Location Resource : 12345\",\n"
                        + "  \"meta\": {\n"
                        + "    \"profile\": [\n"
                        + "      \"http://hl7.org/fhir/profiles/custom-resource\"\n"
                        + "    ]\n"
                        + "  },\n"
                        + "  \"LocationHierarchyTree\": {\n"
                        + "    \"locationsHierarchy\": { \n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"locationId\": \"12345\"\n"
                        + "}";

        FhirContext ctx = FhirContext.forR4Cached();
        ctx.registerCustomType(LocationHierarchy.class);
        IParser parser = ctx.newJsonParser();
        LocationHierarchy locationHierarchy =
                (LocationHierarchy) parser.parseResource(locationHierarchyNoParentChildren);

        List<LocationHierarchy> hierarchies = Arrays.asList(locationHierarchy);
        List<String> attributedLocationIds =
                PractitionerDetailsEndpointHelper.getAttributedLocations(hierarchies);
        Assert.assertNotNull(attributedLocationIds);
        Assert.assertFalse(attributedLocationIds.isEmpty());
        Assert.assertEquals(1, attributedLocationIds.size());
        Assert.assertEquals("12345", attributedLocationIds.get(0));
    }

    private Bundle getPractitionerBundle() {
        Bundle bundlePractitioner = new Bundle();
        bundlePractitioner.setId("Practitioner/1234");
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Practitioner practitioner = new Practitioner();
        practitioner.setId("Practitioner/1234");
        Identifier identifier = new Identifier();
        identifier.setSystem("Secondary");
        identifier.setValue("keycloak-uuid-1234-1234");
        List<Identifier> identifiers = new ArrayList<Identifier>();
        identifiers.add(identifier);
        practitioner.setIdentifier(identifiers);
        bundleEntryComponent.setResource(practitioner);
        bundlePractitioner.addEntry(bundleEntryComponent);
        return bundlePractitioner;
    }
}
