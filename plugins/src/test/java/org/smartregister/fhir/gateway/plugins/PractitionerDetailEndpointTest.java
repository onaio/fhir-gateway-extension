// package org.smartregister.fhir.gateway.plugins;
//
// import ca.uhn.fhir.context.FhirContext;
// import ca.uhn.fhir.parser.IParser;
// import ca.uhn.fhir.rest.client.api.IGenericClient;
// import com.google.fhir.gateway.TokenVerifier;
// import org.apache.http.HttpStatus;
// import org.hl7.fhir.r4.model.*;
// import org.junit.Before;
// import org.junit.Test;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.smartregister.model.practitioner.FhirPractitionerDetails;
// import org.smartregister.model.practitioner.PractitionerDetails;
//
// import javax.servlet.http.HttpServletRequest;
// import javax.servlet.http.HttpServletResponse;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.List;
//
// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.*;
//
// public class PractitionerDetailEndpointTest {
//
//    @Mock
//    private TokenVerifier tokenVerifier;
//
//    @Mock
//    private FhirContext fhirContext;
//
//    @Mock
//    private IGenericClient r4FhirClient;
//
//    @Mock
//    private IParser fhirR4JsonParser;
//
//    @Mock
//    private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;
//
//    @InjectMocks
//    private PractitionerDetailEndpoint practitionerDetailEndpoint;
//
//    @Before
//    public void setUp() {
//        MockitoAnnotations.openMocks(this);
//    }
//
//    @Test
//    public void testDoGet() throws IOException {
//        // Arrange
//        HttpServletRequest request = mock(HttpServletRequest.class);
//        HttpServletResponse response = mock(HttpServletResponse.class);
//
// when(fhirContext.newRestfulGenericClient(System.getenv(Constants.PROXY_TO_ENV))).thenReturn(r4FhirClient);
//        doNothing().when(tokenVerifier).decodeAndVerifyBearerToken(anyString());
//
// when(practitionerDetailsEndpointHelper.getPractitionerDetailsByKeycloakId(anyString())).thenReturn(getPractitionerDetails());
//        HttpServletRequest httpServletRequest = null;
//        HttpServletResponse httpServletResponse = null;
//
//        // Mocking fhirR4JsonParser
//        String encodedPractitionerDetails = "encodedPractitionerDetails";
//
// when(fhirR4JsonParser.encodeResourceToString(any())).thenReturn(encodedPractitionerDetails);
// practitionerDetailEndpoint.setPractitionerDetailsEndpointHelper(practitionerDetailsEndpointHelper);
// practitionerDetailEndpoint.setR4FhirClient(r4FhirClient);
// practitionerDetailEndpoint.setFhirR4JsonParser(fhirR4JsonParser);
// practitionerDetailEndpoint.setFhirR4Context(fhirContext);
//        practitionerDetailEndpoint.doGet(httpServletRequest,httpServletResponse);
//        // Assert
//        verify(response).setContentType("application/json");
//        verify(response.getWriter()).print(encodedPractitionerDetails);
//        verify(response).setStatus(HttpStatus.SC_OK);
//
//    }
//
//    private List<Practitioner> getPractitioners() {
//        List<Practitioner> practitioners = new ArrayList<>();
//        Practitioner practitioner = new Practitioner();
//        practitioner.setActive(true);
//        practitioner.setId("1");
//        practitioners.add(practitioner);
//        return practitioners;
//    }
//
//    private List<CareTeam> getCareTeams() {
//        List<CareTeam> careTeams = new ArrayList<>();
//        CareTeam careTeam = new CareTeam();
//        careTeam.setName("Test Care Team");
//        careTeam.setId("1");
//        careTeams.add(careTeam);
//        return careTeams;
//    }
//
//    private List<PractitionerRole> getPractitionerRoles() {
//        List<PractitionerRole> practitionerRoles = new ArrayList<>();
//        PractitionerRole practitionerRole = new PractitionerRole();
//        practitionerRole.setActive(true);
//        practitionerRole.setId("1");
//        practitionerRoles.add(practitionerRole);
//        return practitionerRoles;
//    }
//
//    private List<Organization> getOrganizations() {
//        List<Organization> organizations = new ArrayList<>();
//        Organization organization = new Organization();
//        organization.setId("1");
//        organization.setName("Test Organization");
//        organizations.add(organization);
//        return organizations;
//    }
//
//    private List<OrganizationAffiliation> getOrganizationAffiliations() {
//        List<OrganizationAffiliation> organizationsAffiliations = new ArrayList<>();
//        OrganizationAffiliation organizationAffiliation = new OrganizationAffiliation();
//        organizationAffiliation.setId("1");
//        List<Reference> locationReferences = new ArrayList<>();
//        Reference locationRef = new Reference();
//        locationRef.setReference("Location/140");
//        locationRef.setDisplay("Location Reference");
//        locationReferences.add(locationRef);
//        organizationAffiliation.setLocation(locationReferences);
//        organizationsAffiliations.add(organizationAffiliation);
//        return organizationsAffiliations;
//    }
//
//    private List<Location> getLocations() {
//        List<Location> locations = new ArrayList<>();
//        Location location = new Location();
//        location.setId("1");
//        location.setName("Test Location");
//        locations.add(location);
//        return locations;
//    }
//
//    private List<Group> getGroups() {
//        List<Group> groups = new ArrayList<>();
//        Group group = new Group();
//        group.setId("1");
//        group.setName("Test Group");
//        groups.add(group);
//        return groups;
//    }
//
//    private PractitionerDetails getPractitionerDetails() {
//        PractitionerDetails practitionerDetails = new PractitionerDetails();
//        FhirPractitionerDetails fhirPractitionerDetails = new FhirPractitionerDetails();
//        fhirPractitionerDetails.setCareTeams(getCareTeams());
//        fhirPractitionerDetails.setOrganizations(getOrganizations());
//        fhirPractitionerDetails.setLocations(getLocations());
//        fhirPractitionerDetails.setOrganizationAffiliations(getOrganizationAffiliations());
//        fhirPractitionerDetails.setPractitioners(getPractitioners());
//        fhirPractitionerDetails.setPractitionerRoles(getPractitionerRoles());
//        fhirPractitionerDetails.setGroups(getGroups());
//
//        practitionerDetails.setFhirPractitionerDetails(fhirPractitionerDetails);
//        return practitionerDetails;
//    }
//
// }
