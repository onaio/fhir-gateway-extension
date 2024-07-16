package org.smartregister.fhir.gateway.plugins;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.model.location.LocationHierarchy;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class LocationHierarchyEndpointHelperTest {

    private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;
    IGenericClient client;

    @Before
    public void setUp() {
        client = mock(IGenericClient.class, new ReturnsDeepStubs());
        locationHierarchyEndpointHelper = new LocationHierarchyEndpointHelper(client);
    }

    @Test
    public void testGetLocationHierarchyNotFound() {
        Mockito.doThrow(ResourceNotFoundException.class)
                .when(client)
                .fetchResourceFromUrl(any(), any());
        LocationHierarchy locationHierarchy =
                locationHierarchyEndpointHelper.getLocationHierarchy("non-existent", null);
        assertEquals(
                org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND,
                locationHierarchy.getId());
    }

    @Test
    public void testGetLocationHierarchyFound() {
        Location location = new Location();
        location.setId("12345");

        Mockito.doReturn(location)
                .when(client)
                .fetchResourceFromUrl(Location.class, "Location/12345");
        LocationHierarchy locationHierarchy =
                locationHierarchyEndpointHelper.getLocationHierarchy("12345", null);
        assertEquals("Location Resource : 12345", locationHierarchy.getId());
    }

    @Test
    public void testGetPaginatedLocationsPaginatesLocations() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("12345").when(request).getParameter(Constants.IDENTIFIER);
        Mockito.doReturn("2").when(request).getParameter(Constants.PAGINATION_PAGE_SIZE);
        Mockito.doReturn("2").when(request).getParameter(Constants.PAGINATION_PAGE_NUMBER);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();
        // Populate the HashMap with the specified parameters
        parameters.put(Constants.IDENTIFIER, new String[] {"12345"});
        parameters.put(Constants.PAGINATION_PAGE_SIZE, new String[] {"2"});
        parameters.put(Constants.PAGINATION_PAGE_NUMBER, new String[] {"2"});

        Mockito.doReturn(parameters).when(request).getParameterMap();

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        List<Location> locations = createLocationList(5, false);
        List<String> adminLevels = new ArrayList<>();

        List<String> locationIds = Collections.singletonList("12345");
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocations(request, locationIds);
        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .getDescendants("12345", null, adminLevels);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.getPaginatedLocations(request, locationIds);

        Assert.assertTrue(resultBundle.hasEntry());
        Assert.assertTrue(resultBundle.hasLink());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(2, resultBundle.getEntry().size());
        Assert.assertEquals(3, resultBundle.getLink().size());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?_page=1&_count=2&_id=12345",
                resultBundle.getLink("previous").getUrl());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?_page=2&_count=2&_id=12345",
                resultBundle.getLink("self").getUrl());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?_page=3&_count=2&_id=12345",
                resultBundle.getLink("next").getUrl());
    }

    @Test
    public void testExtractSyncLocations() {
        String syncLocationsParam = "loc1,loc2,loc3";
        List<String> syncLocations =
                locationHierarchyEndpointHelper.extractSyncLocations(syncLocationsParam);
        assertEquals(3, syncLocations.size());
        assertEquals("loc1", syncLocations.get(0));
        assertEquals("loc2", syncLocations.get(1));
        assertEquals("loc3", syncLocations.get(2));
    }

    @Test
    public void testHandleNonIdentifierRequestListModePaginatesLocations() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("list").when(request).getParameter(Constants.MODE);
        Mockito.doReturn("1,2,3,4").when(request).getParameter(Constants.SYNC_LOCATIONS);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.MODE, new String[] {"list"});
        parameters.put(Constants.SYNC_LOCATIONS, new String[] {"1,2,3,4"});
        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);
        MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class);

        List<Location> locations = createLocationList(4, false);
        List<String> locationIds = List.of("1", "2", "3", "4");
        List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);

        Mockito.doReturn(parameters).when(request).getParameterMap();
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocations(request, locationIds);
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .extractSyncLocations("1,2,3,4");
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleNonIdentifierRequest(
                        request, mockPractitionerDetailsEndpointHelper, mockDecodedJWT);
        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .getDescendants(Mockito.anyString(), Mockito.any(), Mockito.any());
        Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                .when(mockLocationHierarchyEndpointHelper)
                .getSyncStrategyByAppId(Mockito.any());

        mockJwtUtils
                .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                .thenReturn(userRoles);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(
                        request, mockPractitionerDetailsEndpointHelper, mockDecodedJWT);

        Assert.assertTrue(resultBundle.hasEntry());
        Assert.assertTrue(resultBundle.hasLink());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(16, resultBundle.getEntry().size());
    }

    @Test
    public void testGenerateAdminLevelsWithMinAndMax() {
        List<String> adminLevels = locationHierarchyEndpointHelper.generateAdminLevels("1", "3");
        List<String> expectedLevels = new ArrayList<>();
        expectedLevels.add("1");
        expectedLevels.add("2");
        expectedLevels.add("3");
        assertEquals(expectedLevels, adminLevels);
    }

    @Test
    public void testGenerateAdminLevelsWithMinOnly() {
        List<String> adminLevels = locationHierarchyEndpointHelper.generateAdminLevels("2", null);
        List<String> expectedLevels = new ArrayList<>();
        for (int i = 2; i <= Constants.DEFAULT_MAX_ADMIN_LEVEL; i++) {
            expectedLevels.add(String.valueOf(i));
        }
        assertEquals(expectedLevels, adminLevels);
    }

    @Test
    public void testGenerateAdminLevelsWithMaxOnly() {
        List<String> adminLevels = locationHierarchyEndpointHelper.generateAdminLevels(null, "2");
        List<String> expectedLevels = new ArrayList<>();
        expectedLevels.add("0");
        expectedLevels.add("1");
        expectedLevels.add("2");
        assertEquals(expectedLevels, adminLevels);
    }

    @Test
    public void testGenerateAdminLevelsWithNoMinAndMax() {
        List<String> adminLevels = locationHierarchyEndpointHelper.generateAdminLevels(null, null);
        List<String> expectedLevels = new ArrayList<>();
        assertEquals(expectedLevels, adminLevels);
    }

    @Test(expected = RuntimeException.class)
    public void testGenerateAdminLevelsWithInvalidRange() {
        locationHierarchyEndpointHelper.generateAdminLevels("3", "1");
    }

    @Test
    public void testGetDecendantsWithAdminLevelFiltersReturnsLocationsWithinAdminLevel() {
        String locationId = "12345";
        Location parentLocation = new Location();
        parentLocation.setId(locationId);

        List<String> adminLevels = new ArrayList<>();
        adminLevels.add("1");
        adminLevels.add("2");
        adminLevels.add("3");

        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle firstBundleMock = new Bundle();
        List<Bundle.BundleEntryComponent> firstBundleEntries = new ArrayList<>();
        Location firstChildLocation = new Location();
        firstChildLocation.setId("54321");
        firstBundleEntries.add(new Bundle.BundleEntryComponent().setResource(firstChildLocation));
        firstBundleMock.setEntry(firstBundleEntries);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).forResource(Location.class);
        Mockito.doReturn(queryMock).when(queryMock).where(any(ICriterion.class));
        Mockito.doReturn(queryMock).when(queryMock).and(any(ICriterion.class));
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        Mockito.doReturn(firstBundleMock, secondBundleMock).when(queryMock).execute();

        List<Location> descendants =
                locationHierarchyEndpointHelper.getDescendants(
                        locationId, parentLocation, adminLevels);

        Assert.assertNotNull(descendants);
        Assert.assertEquals(2, descendants.size());
        Assert.assertEquals(locationId, descendants.get(0).getId());
        Assert.assertEquals("54321", descendants.get(1).getId());

        verify(queryMock, times(2)).execute();
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

    private List<Location> createLocationList(int numLocations, boolean setAdminLevel) {
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < numLocations; i++) {
            Location location = new Location();
            location.setId(Integer.toString(i));
            locations.add(location);
            if (setAdminLevel) {
                CodeableConcept type = new CodeableConcept();
                Coding coding = new Coding();
                coding.setSystem(Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL);
                coding.setCode(Integer.toString(i));
                coding.setDisplay(String.format("Level %d", i));
                type.addCoding(coding);
                location.addType(type);
            }
        }
        return locations;
    }
}
