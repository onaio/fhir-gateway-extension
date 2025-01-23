package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.smartregister.fhir.gateway.plugins.PractitionerDetailsEndpointHelper.EMPTY_BUNDLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.OrganizationAffiliation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
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
                practitionerDetailsEndpointHelper.getSupervisorPractitionerDetailsByKeycloakId(
                        "222");
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
                .getPractitionerByIdentifier("keycloak-uuid-1234-1234");
        Mockito.doReturn(bundlePractitioner)
                .when(mockPractitionerDetailsEndpointHelper)
                .getAttributedPractitionerDetailsByPractitioner(practitioner);
        Mockito.doCallRealMethod()
                .when(mockPractitionerDetailsEndpointHelper)
                .getSupervisorPractitionerDetailsByKeycloakId("keycloak-uuid-1234-1234");
        Bundle resultBundle =
                mockPractitionerDetailsEndpointHelper.getSupervisorPractitionerDetailsByKeycloakId(
                        "keycloak-uuid-1234-1234");
        Assert.assertNotNull(resultBundle);
        assertEquals(1, resultBundle.getEntry().size());
        assertEquals("Practitioner/1234", resultBundle.getEntry().get(0).getResource().getId());
    }

    @Test
    public void
            testGetAttributedPractitionerDetailsByPractitionerWithPractitionerReturnsAttributedPractitioner() {
        Practitioner practitioner = getPractitioner();
        CareTeam careTeam = getCareTeam();
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        List<CareTeam> careTeams = new ArrayList<>();
        careTeams.add(careTeam);
        List<LocationHierarchy> locationHierarchies = new ArrayList<>();
        locationHierarchies.add(locationHierarchy);
        String id = "1234";
        Set<String> ids = new HashSet<>();
        ids.add(id);
        List<String> stringIds = new ArrayList<>();
        stringIds.add(id);
        List<OrganizationAffiliation> organizationAffiliations = new ArrayList<>();
        organizationAffiliations.add(getOrganizationAffiliation());
        PractitionerDetails practitionerDetails = getPractitionerDetails();
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);

        Mockito.doReturn(practitionerDetails)
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerDetailsByPractitioner(practitioner);
        Mockito.doReturn(ids)
                .when(mockPractitionerDetailsEndpointHelper)
                .getManagingOrganizationsOfCareTeamIds(careTeams);
        Mockito.doReturn(organizationAffiliations)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationAffiliationsByOrganizationIds(ids);
        Mockito.doReturn(stringIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getLocationIdsByOrganizationAffiliations(organizationAffiliations);

        MockedStatic<PractitionerDetailsEndpointHelper>
                mockStaticPractitionerDetailsEndpointHelper =
                        Mockito.mockStatic(PractitionerDetailsEndpointHelper.class);
        mockStaticPractitionerDetailsEndpointHelper
                .when(() -> PractitionerDetailsEndpointHelper.getLocationsHierarchy(stringIds))
                .thenReturn(locationHierarchies);
        mockStaticPractitionerDetailsEndpointHelper
                .when(
                        () ->
                                PractitionerDetailsEndpointHelper.getAttributedLocations(
                                        locationHierarchies))
                .thenReturn(ids);

        Mockito.doReturn(stringIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationIdsByLocationIds(ids);

        Mockito.doReturn(careTeams)
                .when(mockPractitionerDetailsEndpointHelper)
                .getCareTeamsByOrganizationIds(Mockito.any());
        Mockito.doCallRealMethod()
                .when(mockPractitionerDetailsEndpointHelper)
                .getAttributedPractitionerDetailsByPractitioner(practitioner);

        Bundle resultBundle =
                mockPractitionerDetailsEndpointHelper
                        .getAttributedPractitionerDetailsByPractitioner(practitioner);
        Assert.assertNotNull(resultBundle);
        Assert.assertEquals(1, resultBundle.getTotal());
        Assert.assertEquals(1, resultBundle.getEntry().size());
        mockStaticPractitionerDetailsEndpointHelper.close();
    }

    @Test
    public void testGetOrganizationIdsByLocationIdsWithEmptyLocationsReturnsEmptyArray() {
        Set<String> emptyLocationIds = new HashSet<>();
        List<String> organizationIds =
                practitionerDetailsEndpointHelper.getOrganizationIdsByLocationIds(emptyLocationIds);
        Assert.assertTrue(organizationIds.isEmpty());
    }

    @Test
    public void testGetOrganizationIdsByLocationIdsWithLocationIdsReturnsOrganizationIds() {
        Bundle mockOrganizationAffiliationBundle = new Bundle();
        OrganizationAffiliation orgAffiliation1 = new OrganizationAffiliation();
        orgAffiliation1.setOrganization(new Reference("Organization/1234"));

        OrganizationAffiliation orgAffiliation2 = new OrganizationAffiliation();
        orgAffiliation2.setOrganization(new Reference("Organization/5678"));

        Bundle.BundleEntryComponent entry1 = new Bundle.BundleEntryComponent();
        entry1.setResource(orgAffiliation1);
        Bundle.BundleEntryComponent entry2 = new Bundle.BundleEntryComponent();
        entry2.setResource(orgAffiliation2);

        mockOrganizationAffiliationBundle.addEntry(entry1);
        mockOrganizationAffiliationBundle.addEntry(entry2);

        Object whenOrganizationAffiliationSearch =
                client.search()
                        .forResource(eq(OrganizationAffiliation.class))
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenOrganizationAffiliationSearch).thenReturn(mockOrganizationAffiliationBundle);
        Set<String> locationIds = new HashSet<>(Arrays.asList("Location1", "Location2"));

        List<String> organizationIds =
                practitionerDetailsEndpointHelper.getOrganizationIdsByLocationIds(locationIds);

        Assert.assertNotNull(organizationIds);
        Assert.assertEquals(2, organizationIds.size());
        Assert.assertTrue(organizationIds.contains("1234"));
        Assert.assertTrue(organizationIds.contains("5678"));
    }

    @Test
    public void testGetCareTeamsByOrganizationIdsWithOrganizationIdsReturnsCorrectCareTeams() {
        List<String> organizationIds = Arrays.asList("1", "2", "3");
        CareTeam careTeam1 = new CareTeam();
        careTeam1.setId("CareTeam/1");
        careTeam1.setManagingOrganization(Arrays.asList(new Reference("Organization/1")));
        CareTeam careTeam2 = new CareTeam();
        careTeam2.setId("CareTeam/2");
        careTeam2.setManagingOrganization(Arrays.asList(new Reference("Organization/2")));
        CareTeam careTeam3 = new CareTeam();
        careTeam3.setId("CareTeam/3");
        careTeam3.setManagingOrganization(Arrays.asList(new Reference("Organization/3")));
        Bundle bundle = new Bundle();
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(careTeam1));
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(careTeam2));
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(careTeam3));

        Object whenCareTeamSearch =
                client.search()
                        .forResource(CareTeam.class)
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenCareTeamSearch).thenReturn(bundle);
        List<CareTeam> result =
                practitionerDetailsEndpointHelper.getCareTeamsByOrganizationIds(organizationIds);
        Assert.assertEquals(3, result.size());
        Assert.assertTrue(result.stream().anyMatch(ct -> ct.getId().equals("CareTeam/1")));
        Assert.assertTrue(result.stream().anyMatch(ct -> ct.getId().equals("CareTeam/2")));
        Assert.assertTrue(result.stream().anyMatch(ct -> ct.getId().equals("CareTeam/3")));
    }

    @Test
    public void testGetOrganizationsByIdWithEmptyOrganizationIdsReturnsEmptyBundle() {
        Set<String> organizationIds = Collections.emptySet();
        Bundle result = practitionerDetailsEndpointHelper.getOrganizationsById(organizationIds);
        Assert.assertSame(EMPTY_BUNDLE, result);
    }

    @Test
    public void testGetOrganizationsByIdWithValidOrganizationIdsReturnsOrganizations() {
        Set<String> organizationIds = new HashSet<>(Arrays.asList("1", "2"));

        Organization org1 = new Organization();
        org1.setId("Organization/1");

        Organization org2 = new Organization();
        org2.setId("Organization/2");

        Bundle bundle = new Bundle();
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(org1));
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(org2));

        Object whenSearch =
                client.search()
                        .forResource(Organization.class)
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenSearch).thenReturn(bundle);
        Bundle result = practitionerDetailsEndpointHelper.getOrganizationsById(organizationIds);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.getEntry().size());
        Assert.assertEquals("Organization/1", result.getEntry().get(0).getResource().getId());
        Assert.assertEquals("Organization/2", result.getEntry().get(1).getResource().getId());
    }

    @Test
    public void testGetLocationsByIdsWithNullLocationIdsReturnsEmptyResult() {
        List<String> locationIds = null;
        List<Location> result = practitionerDetailsEndpointHelper.getLocationsByIds(locationIds);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetLocationsByIdsWithEmptyLocationIdsReturnsEmptyResult() {
        List<String> locationIds = new ArrayList<>();
        List<Location> result = practitionerDetailsEndpointHelper.getLocationsByIds(locationIds);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetLocationsByIdsWithValidLocationIdsReturnsLocations() {
        List<String> locationIds = Arrays.asList("1", "2");
        Location location1 = new Location();
        location1.setId("Location/1");
        Location location2 = new Location();
        location2.setId("Location/2");
        Bundle bundle = new Bundle();
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(location1));
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(location2));

        Object whenSearch =
                client.search()
                        .forResource(Location.class)
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenSearch).thenReturn(bundle);
        List<Location> result = practitionerDetailsEndpointHelper.getLocationsByIds(locationIds);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Location/1", result.get(0).getId());
        Assert.assertEquals("Location/2", result.get(1).getId());
    }

    @Test
    public void
            testGetOrganizationAffiliationsByOrganizationIdsWithNullOrganizationIdsReturnsEmptyResult() {
        Set<String> organizationIds = null;
        List<OrganizationAffiliation> result =
                practitionerDetailsEndpointHelper.getOrganizationAffiliationsByOrganizationIds(
                        organizationIds);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void
            testGetOrganizationAffiliationsByOrganizationIdsWithEmptyOrganizationIdsReturnsEmptyResult() {
        Set<String> organizationIds = Collections.emptySet();
        List<OrganizationAffiliation> result =
                practitionerDetailsEndpointHelper.getOrganizationAffiliationsByOrganizationIds(
                        organizationIds);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void
            testGetOrganizationAffiliationsByOrganizationIdsWithValidOrganizationIdsReturnsOrganizationAffiliations() {
        Set<String> organizationIds = new HashSet<>(Arrays.asList("1", "2"));
        OrganizationAffiliation affiliation1 = new OrganizationAffiliation();
        affiliation1.setId("OrganizationAffiliation/1");
        OrganizationAffiliation affiliation2 = new OrganizationAffiliation();
        affiliation2.setId("OrganizationAffiliation/2");

        Bundle bundle = new Bundle();
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(affiliation1));
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(affiliation2));

        Object whenSearch =
                client.search()
                        .forResource(OrganizationAffiliation.class)
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenSearch).thenReturn(bundle);
        List<OrganizationAffiliation> result =
                practitionerDetailsEndpointHelper.getOrganizationAffiliationsByOrganizationIds(
                        organizationIds);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("OrganizationAffiliation/1", result.get(0).getId());
        Assert.assertEquals("OrganizationAffiliation/2", result.get(1).getId());
    }

    @Test
    public void
            testGetOrganizationAffiliationsByOrganizationIdsBundleWithEmptyOrganizationIdsReturnsEmptyBundle() {
        Set<String> organizationIds = Collections.emptySet();
        Bundle result =
                practitionerDetailsEndpointHelper
                        .getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);
        Assert.assertSame(EMPTY_BUNDLE, result);
    }

    @Test
    public void
            testGetOrganizationAffiliationsByOrganizationIdsBundleWithValidOrganizationIdsReturnsOrganizationAffiliations() {
        Set<String> organizationIds = new HashSet<>(Arrays.asList("1", "2"));
        OrganizationAffiliation affiliation1 = new OrganizationAffiliation();
        affiliation1.setId("OrganizationAffiliation/1");

        Bundle bundle = new Bundle();
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(affiliation1));
        Object whenSearch =
                client.search()
                        .forResource(OrganizationAffiliation.class)
                        .where(any(ICriterion.class))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        when(whenSearch).thenReturn(bundle);
        Bundle result =
                practitionerDetailsEndpointHelper
                        .getOrganizationAffiliationsByOrganizationIdsBundle(organizationIds);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.getEntry().size());
        Assert.assertEquals(
                "OrganizationAffiliation/1", result.getEntry().get(0).getResource().getId());
    }

    @Test
    public void
            testGetLocationIdsByOrganizationAffiliationsWithEmptyAffiliationsReturnsEmptyResult() {
        List<OrganizationAffiliation> affiliations = Collections.emptyList();
        List<String> result =
                practitionerDetailsEndpointHelper.getLocationIdsByOrganizationAffiliations(
                        affiliations);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void
            testGetLocationIdsByOrganizationAffiliationsWithValidAffiliationsReturnsLocationIds() {
        OrganizationAffiliation affiliation1 = new OrganizationAffiliation();
        affiliation1.addLocation(new Reference("Location/1"));
        OrganizationAffiliation affiliation2 = new OrganizationAffiliation();
        affiliation2.addLocation(new Reference("Location/2"));
        List<OrganizationAffiliation> affiliations = Arrays.asList(affiliation1, affiliation2);
        List<String> result =
                practitionerDetailsEndpointHelper.getLocationIdsByOrganizationAffiliations(
                        affiliations);

        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("1", result.get(0));
        Assert.assertEquals("2", result.get(1));
    }

    @Test
    public void testGetPractitionerLocationIdsByByKeycloakIdCoreReturnsLocationIds() {
        String practitionerId = "keycloak-uuid-1234-1234";
        Bundle careTeamBundle = getPractitionerBundle();
        List<CareTeam> careTeamList = new ArrayList<>();
        careTeamList.add(getCareTeam());
        Set<String> careTeamManagingOrganizationIds = new HashSet<>();
        careTeamManagingOrganizationIds.add("Organization/1234");

        List<PractitionerRole> practitionerRoleList = getPractitionerRoleList();
        Set<String> practitionerOrganizationIds = new HashSet<>();
        practitionerOrganizationIds.add("Organization/5678");

        Set<String> combinedOrganizationIds = new HashSet<>();
        combinedOrganizationIds.addAll(careTeamManagingOrganizationIds);
        combinedOrganizationIds.addAll(practitionerOrganizationIds);

        Bundle organizationAffiliationsBundle = getOrganizationAffiliationsBundle();
        List<OrganizationAffiliation> organizationAffiliations = new ArrayList<>();

        organizationAffiliations.add(getOrganizationAffiliation());

        List<String> locationIds = new ArrayList<>();
        locationIds.add("Location/1234");

        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);

        Mockito.doReturn(careTeamBundle)
                .when(mockPractitionerDetailsEndpointHelper)
                .getCareTeams(practitionerId);
        Mockito.doReturn(careTeamList)
                .when(mockPractitionerDetailsEndpointHelper)
                .mapBundleToCareTeams(careTeamBundle);
        Mockito.doReturn(careTeamManagingOrganizationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getManagingOrganizationsOfCareTeamIds(careTeamList);
        Mockito.doReturn(practitionerRoleList)
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerRolesByPractitionerId(practitionerId);
        Mockito.doReturn(practitionerOrganizationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationIdsByPractitionerRoles(practitionerRoleList);
        Mockito.doReturn(organizationAffiliationsBundle)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationAffiliationsByOrganizationIdsBundle(combinedOrganizationIds);
        Mockito.doReturn(organizationAffiliations)
                .when(mockPractitionerDetailsEndpointHelper)
                .mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);
        Mockito.doReturn(locationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getLocationIdsByOrganizationAffiliations(organizationAffiliations);

        Mockito.doCallRealMethod()
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerLocationIdsByByKeycloakIdCore(practitionerId);
        List<String> resultLocationIds =
                mockPractitionerDetailsEndpointHelper.getPractitionerLocationIdsByByKeycloakIdCore(
                        practitionerId);

        Assert.assertNotNull(resultLocationIds);
        Assert.assertEquals(1, resultLocationIds.size());
        Assert.assertEquals("Location/1234", resultLocationIds.get(0));
    }

    @Test
    public void
            testGetPractitionerDetailsByPractitionerCorePopulatesPractitionerDetailsContainedField() {
        String practitionerId = "keycloak-uuid-1234-1234";
        Bundle careTeamBundle = getPractitionerBundle();
        List<CareTeam> careTeamList = new ArrayList<>();
        careTeamList.add(getCareTeam());
        Set<String> careTeamManagingOrganizationIds = new HashSet<>();
        careTeamManagingOrganizationIds.add("Organization/1234");

        List<PractitionerRole> practitionerRoleList = getPractitionerRoleList();
        Set<String> practitionerOrganizationIds = new HashSet<>();
        practitionerOrganizationIds.add("Organization/5678");

        Set<String> combinedOrganizationIds = new HashSet<>();
        combinedOrganizationIds.addAll(careTeamManagingOrganizationIds);
        combinedOrganizationIds.addAll(practitionerOrganizationIds);

        Bundle organizationAffiliationsBundle = getOrganizationAffiliationsBundle();
        List<OrganizationAffiliation> organizationAffiliations = new ArrayList<>();

        organizationAffiliations.add(getOrganizationAffiliation());

        List<String> locationIds = new ArrayList<>();
        locationIds.add("Location/1234");
        List<Location> locations = new ArrayList<>();
        locations.add(getLocation());

        List<Organization> organizationList = new ArrayList<>();
        organizationList.add(getOrganization());

        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);

        Mockito.doReturn(careTeamBundle)
                .when(mockPractitionerDetailsEndpointHelper)
                .getCareTeams(practitionerId);
        Mockito.doReturn(careTeamList)
                .when(mockPractitionerDetailsEndpointHelper)
                .mapBundleToCareTeams(careTeamBundle);
        Mockito.doReturn(careTeamManagingOrganizationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getManagingOrganizationsOfCareTeamIds(careTeamList);
        Mockito.doReturn(organizationList)
                .when(mockPractitionerDetailsEndpointHelper)
                .mapBundleToOrganizations(mock(Bundle.class));
        Mockito.doReturn(practitionerRoleList)
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerRolesByPractitionerId(practitionerId);
        Mockito.doReturn(practitionerOrganizationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationIdsByPractitionerRoles(practitionerRoleList);
        Mockito.doReturn(organizationAffiliationsBundle)
                .when(mockPractitionerDetailsEndpointHelper)
                .getOrganizationAffiliationsByOrganizationIdsBundle(combinedOrganizationIds);
        Mockito.doReturn(organizationAffiliations)
                .when(mockPractitionerDetailsEndpointHelper)
                .mapBundleToOrganizationAffiliation(organizationAffiliationsBundle);
        Mockito.doReturn(locationIds)
                .when(mockPractitionerDetailsEndpointHelper)
                .getLocationIdsByOrganizationAffiliations(organizationAffiliations);
        Mockito.doReturn(locations)
                .when(mockPractitionerDetailsEndpointHelper)
                .getLocationsByIds(locationIds);
        Practitioner practitioner = getPractitioner();
        Mockito.doCallRealMethod()
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerDetailsByPractitionerCore(practitionerId, practitioner);

        PractitionerDetails practitionerDetails =
                mockPractitionerDetailsEndpointHelper.getPractitionerDetailsByPractitionerCore(
                        practitionerId, practitioner);
        Assert.assertNotNull(practitionerDetails);
        CareTeam containedCareTeam = (CareTeam) practitionerDetails.getContained().get(0);
        Assert.assertEquals("CareTeam/1234", containedCareTeam.getId());
        Practitioner containedPractitioner =
                (Practitioner) practitionerDetails.getContained().get(1);
        Assert.assertEquals("Practitioner/1234", containedPractitioner.getId());
        PractitionerRole containedPractitionerRole =
                (PractitionerRole) practitionerDetails.getContained().get(2);
        Assert.assertEquals("PractitionerRole/1234", containedPractitionerRole.getId());
        OrganizationAffiliation containedOrganizationAffiliation =
                (OrganizationAffiliation) practitionerDetails.getContained().get(3);
        Assert.assertEquals(
                "OrganizationAffiliation/1234", containedOrganizationAffiliation.getId());
        Location containedLocation = (Location) practitionerDetails.getContained().get(4);
        Assert.assertEquals("Location/1234", containedLocation.getId());
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

    private OrganizationAffiliation getOrganizationAffiliation() {
        OrganizationAffiliation organizationAffiliation = new OrganizationAffiliation();
        organizationAffiliation.setId("OrganizationAffiliation/1234");
        return organizationAffiliation;
    }

    private CareTeam getCareTeam() {
        CareTeam careTeam = new CareTeam();
        careTeam.setId("CareTeam/1234");
        CareTeam.CareTeamParticipantComponent participant =
                new CareTeam.CareTeamParticipantComponent();
        participant.setMember(new Reference("Practitioner/1234"));
        careTeam.addParticipant(participant);
        return careTeam;
    }

    private Bundle getOrganizationAffiliationsBundle() {
        Bundle bundle = new Bundle();
        bundle.setId("OrganizationAffiliationsBundle/1234");
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        OrganizationAffiliation organizationAffiliation = getOrganizationAffiliation();
        bundleEntryComponent.setResource(organizationAffiliation);
        bundle.addEntry(bundleEntryComponent);
        return bundle;
    }

    private List<PractitionerRole> getPractitionerRoleList() {
        PractitionerRole practitionerRole = new PractitionerRole();
        practitionerRole.setId("PractitionerRole/1234");
        Reference organizationRef = new Reference();
        organizationRef.setReference("Organization/1234");
        practitionerRole.setOrganization(organizationRef);
        Reference practitionerRef = new Reference();
        practitionerRef.setReference("Practitioner/1234");
        practitionerRole.setPractitioner(practitionerRef);
        List<PractitionerRole> practitionerRoles = new ArrayList<>();
        practitionerRoles.add(practitionerRole);
        return practitionerRoles;
    }

    private Organization getOrganization() {
        Organization organization = new Organization();
        organization.setId("organization-id-1");
        organization.setName("test organization");
        return organization;
    }

    private Location getLocation() {
        Location location = new Location();
        location.setId("Location/1234");
        location.setName("test-locations");
        return location;
    }
}
