package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.practitioner.PractitionerDetails;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;

import java.util.ArrayList;
import java.util.List;

public class PractitonerDetailsEndpointHelperTest {

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
                practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId("keycloak-uuid-1234-1234");
        assertEquals("keycloak-uuid-1234-1234", practitionerDetails.getFhirPractitionerDetails().getPractitioners().get(0).getIdentifier().get(0).getValue());
        assertEquals("Practitioner/1234", practitionerDetails.getFhirPractitionerDetails().getPractitioners().get(0).getId());
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
