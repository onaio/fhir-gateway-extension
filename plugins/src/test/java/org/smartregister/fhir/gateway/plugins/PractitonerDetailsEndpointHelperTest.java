package org.smartregister.fhir.gateway.plugins;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IHistoryUntyped;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.practitioner.PractitionerDetails;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        Object whenObj = client
                .search()
                .forResource(eq(Practitioner.class))
                .where(any(ICriterion.class))
                .returnBundle((Class<IBaseBundle>)any())
                .execute();

        when(whenObj).thenReturn(bundlePractitioner);
        PractitionerDetails practitionerDetails = practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId("111");
       assertEquals(org.smartregister.utils.Constants.PRACTITIONER_NOT_FOUND,practitionerDetails.getId());

    }
}
