package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.practitioner.FhirPractitionerDetails;
import org.smartregister.model.practitioner.PractitionerDetails;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
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
                        .usingStyle(SearchStyleEnum.POST)
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
                        .usingStyle(SearchStyleEnum.POST)
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
        Set<String> attributedLocationIds =
                PractitionerDetailsEndpointHelper.getAttributedLocations(hierarchies);
        Assert.assertNotNull(attributedLocationIds);
        Assert.assertFalse(attributedLocationIds.isEmpty());
        Assert.assertEquals(1, attributedLocationIds.size());
        Assert.assertEquals("12345", attributedLocationIds.iterator().next());
    }

    @Test
    public void testGetSupervisorPractitionerDetailsByKeycloakIdWithInvalidIDReturnsEmptyBundle() {
        Bundle bundlePractitioner = new Bundle();
        Object whenObj =
                client.search()
                        .forResource(eq(Practitioner.class))
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(any())
                        .execute();

        when(whenObj).thenReturn(bundlePractitioner);
        Bundle supervisorBundle =
                practitionerDetailsEndpointHelper.getSupervisorPractitionerDetailsByKeycloakId("222");
        assertEquals(0, supervisorBundle.getEntry().size());
    }

    @Test
    public void testGetSupervisorPractitionerDetailsByKeycloakIdWithValidIDReturnsBundle() {
        Bundle bundlePractitioner = getPractitionerBundle();
        Practitioner practitioner = getPractitioner();
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
            mock(PractitionerDetailsEndpointHelper.class);
        Mockito.doReturn(practitioner)
            .when(mockPractitionerDetailsEndpointHelper)
            .getPractitionerByIdentifier(
                "keycloak-uuid-1234-1234");
        Mockito.doReturn(bundlePractitioner)
            .when(mockPractitionerDetailsEndpointHelper)
            .getAttributedPractitionerDetailsByPractitioner(practitioner);
        Mockito.doCallRealMethod().when(mockPractitionerDetailsEndpointHelper).getSupervisorPractitionerDetailsByKeycloakId("keycloak-uuid-1234-1234");
        Bundle resultBundle = mockPractitionerDetailsEndpointHelper.getSupervisorPractitionerDetailsByKeycloakId("keycloak-uuid-1234-1234");
        Assert.assertNotNull(resultBundle);
        assertEquals(1, resultBundle.getEntry().size());
        assertEquals("Practitioner/1234", resultBundle.getEntry().get(0).getResource().getId());
    }



    private Bundle getPractitionerBundle() {
        Bundle bundlePractitioner = new Bundle();
        bundlePractitioner.setId("Practitioner/1234");
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        Practitioner practitioner = getPractitioner();
        bundleEntryComponent.setResource(practitioner);
        bundlePractitioner.addEntry(bundleEntryComponent);
        return bundlePractitioner;
    }

    private Practitioner getPractitioner() {
        Practitioner practitioner = new Practitioner();
        practitioner.setId("Practitioner/1234");
        Identifier identifier = new Identifier();
        identifier.setSystem("Secondary");
        identifier.setValue("keycloak-uuid-1234-1234");
        List<Identifier> identifiers = new ArrayList<Identifier>();
        identifiers.add(identifier);
        practitioner.setIdentifier(identifiers);
        return practitioner;
    }

    private PractitionerDetails getPractitionerDetails() {
        PractitionerDetails practitionerDetails = new PractitionerDetails();
        practitionerDetails.setId("PractitionerDetails/1234");
        FhirPractitionerDetails fhirPractitionerDetails = getFhirPractitionerDetails();
        practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
        Identifier identifier = new Identifier();
        identifier.setSystem("Secondary");
        identifier.setValue("keycloak-uuid-1234-1234");
        List<Identifier> identifiers = new ArrayList<>();
        identifiers.add(identifier);
        practitionerDetails.setIdentifier(identifiers);
        return practitionerDetails;
    }

    private FhirPractitionerDetails getFhirPractitionerDetails() {
        FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
        fhirPractitionerDetails.setId("FhirPractitionerDetails/1234");
        CareTeam careTeam = getCareTeam();
        List<CareTeam> careTeams = Collections.singletonList(careTeam);
        fhirPractitionerDetails.setCareTeams(careTeams);
        return fhirPractitionerDetails;
    }

    private CareTeam getCareTeam() {
        CareTeam careTeam = new CareTeam();
        careTeam.setId("CareTeam/1234");
        return careTeam;
    }
}
