package org.smartregister.fhir.gateway.plugins.helper;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.SyncAccessDecision;
import org.smartregister.fhir.gateway.plugins.utils.JwtUtils;
import org.smartregister.fhir.gateway.plugins.utils.Utils;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

public class LocationHierarchyEndpointHelperTest {

    IGenericClient client;
    private LocationHierarchyEndpointHelper locationHierarchyEndpointHelper;

    public static List<Location> createTestLocationList(
            int numLocations, boolean setAdminLevel, boolean setLastUpdated) {
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
            if (setLastUpdated) {
                Meta meta = new Meta();
                meta.setLastUpdated(Date.from(OffsetDateTime.now().toInstant()));
                location.setMeta(meta);
            }
        }
        return locations;
    }

    public static LocationHierarchy createLocationHierarchy(List<Location> locations) {
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        locationHierarchyTree.buildTreeFromList(locations);
        locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
        return locationHierarchy;
    }

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
                locationHierarchyEndpointHelper.getLocationHierarchy(
                        "non-existent", null, null, false, "");
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
                locationHierarchyEndpointHelper.getLocationHierarchy(
                        "12345", null, null, false, "");
        assertEquals("Location Resource : 12345", locationHierarchy.getId());
    }

    @SuppressWarnings("removal")
    @Test
    public void testGetPaginatedLocationsBackwardCompatibilityPaginatesLocations() {
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
        List<Location> locations = createTestLocationList(5, false, false);
        List<String> adminLevels = new ArrayList<>();

        List<String> locationIds = Collections.singletonList("12345");

        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .adminLevelFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .lastUpdatedFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .inventoryFilter(Mockito.any(Location.class));
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocationsBackwardCompatibility(request, locationIds);
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .postFetchFilters(locations, adminLevels, false, "");

        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchyLocations("12345", adminLevels, adminLevels, false, null);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.getPaginatedLocationsBackwardCompatibility(
                        request, locationIds);

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
        parameters.put(Constants.FILTER_MODE_LINEAGE, new String[] {"true"});

        Mockito.doReturn(parameters).when(request).getParameterMap();

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        List<Location> locations = createTestLocationList(5, false, false);
        List<String> adminLevels = new ArrayList<>();

        List<String> locationIds = Collections.singletonList("12345");

        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .adminLevelFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .lastUpdatedFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .inventoryFilter(Mockito.any(Location.class));
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocations(request, locationIds, null);
        Mockito.doReturn(Utils.createBundle(locations))
                .when(mockLocationHierarchyEndpointHelper)
                .fetchAllDescendants(List.of("12345"), adminLevels, null);

        Location parentLocation = new Location();
        parentLocation.setId("12345");

        locations.add(parentLocation);

        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .postFetchFilters(locations, adminLevels, false, null);

        Mockito.doReturn(Utils.createBundle(List.of(parentLocation)))
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationsById(List.of("12345"));

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.getPaginatedLocations(
                        request, locationIds, null);

        Assert.assertTrue(resultBundle.hasEntry());
        Assert.assertTrue(resultBundle.hasLink());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(2, resultBundle.getEntry().size());
        Assert.assertEquals(3, resultBundle.getLink().size());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?filter_mode_lineage=true&_page=1&_count=2&_id=12345",
                resultBundle.getLink("previous").getUrl());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?filter_mode_lineage=true&_page=2&_count=2&_id=12345",
                resultBundle.getLink("self").getUrl());
        Assert.assertEquals(
                "http://test:8080/LocationHierarchy?filter_mode_lineage=true&_page=3&_count=2&_id=12345",
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

    @SuppressWarnings("removal")
    @Test
    public void testHandleNonIdentifierRequestListModePaginatesLocations() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("list").when(request).getParameter(Constants.MODE);
        Mockito.doReturn("1,2,3,4")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();

        parameters.put(Constants.MODE, new String[] {"list"});
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"1,2,3,4"});
        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);
        MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class);
        List<String> adminLevels = new ArrayList<>();

        List<Location> locations = createTestLocationList(4, false, false);
        List<String> locationIds = List.of("1", "2", "3", "4");
        List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);

        Mockito.doReturn(parameters).when(request).getParameterMap();
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocationsBackwardCompatibility(request, locationIds);
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .extractSyncLocations("1,2,3,4");
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleNonIdentifierRequest(request, mockDecodedJWT);
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .adminLevelFilter(Mockito.any(), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .lastUpdatedFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .inventoryFilter(Mockito.any(Location.class));
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .postFetchFilters(locations, adminLevels, false, "");
        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchyLocations(
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
        Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                .when(mockLocationHierarchyEndpointHelper)
                .getSyncStrategyByAppId(Mockito.any());

        mockJwtUtils
                .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                .thenReturn(userRoles);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(
                        request, mockDecodedJWT);

        Assert.assertTrue(resultBundle.hasEntry());
        Assert.assertTrue(resultBundle.hasLink());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(16, resultBundle.getEntry().size());
        mockJwtUtils.close();
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
    public void testGenerateAdminLevelsWithBlankMinUsesDefault() {
        List<String> adminLevels =
                locationHierarchyEndpointHelper.generateAdminLevels(
                        StringUtils.SPACE + StringUtils.SPACE, "2");
        List<String> expectedLevels = new ArrayList<>();
        expectedLevels.add("0");
        expectedLevels.add("1");
        expectedLevels.add("2");
        assertEquals(expectedLevels, adminLevels);
    }

    @Test
    public void testGenerateAdminLevelsWithBlankMaxUsesDefault() {
        List<String> adminLevels =
                locationHierarchyEndpointHelper.generateAdminLevels(
                        "2", StringUtils.SPACE + StringUtils.SPACE);
        List<String> expectedLevels = new ArrayList<>();
        for (int i = 2; i <= Constants.DEFAULT_MAX_ADMIN_LEVEL; i++) {
            expectedLevels.add(String.valueOf(i));
        }
        assertEquals(expectedLevels, adminLevels);
    }

    @Test
    public void testGenerateAdminLevelsWithNoMinAndMax() {
        List<String> adminLevels = locationHierarchyEndpointHelper.generateAdminLevels(null, null);
        List<String> expectedLevels = new ArrayList<>();
        // When both min and max are null, defaults to DEFAULT_MIN_ADMIN_LEVEL (0) to
        // DEFAULT_MAX_ADMIN_LEVEL (10)
        for (int i = Constants.DEFAULT_MIN_ADMIN_LEVEL;
                i <= Constants.DEFAULT_MAX_ADMIN_LEVEL;
                i++) {
            expectedLevels.add(String.valueOf(i));
        }
        assertEquals(expectedLevels, adminLevels);
    }

    @Test(expected = RuntimeException.class)
    public void testGenerateAdminLevelsWithInvalidRange() {
        locationHierarchyEndpointHelper.generateAdminLevels("3", "1");
    }

    @Test
    public void testGetDescendantsWithAdminLevelFiltersReturnsLocationsWithinAdminLevel() {
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
        Mockito.doReturn(queryMock).when(queryMock).usingStyle(SearchStyleEnum.POST);
        Mockito.doReturn(queryMock)
                .when(queryMock)
                .count(SyncAccessDecision.SyncAccessDecisionConstants.REL_LOCATION_CHUNK_SIZE);
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

    @Test
    public void testFilterLocationsByAdminLevelsBasic() {
        List<Location> locations = createTestLocationList(5, true, false);
        List<String> adminLevels = List.of("1", "3");

        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(locations, adminLevels, false, "");

        Assert.assertEquals(2, filteredLocations.size());
        Assert.assertEquals("1", filteredLocations.get(0).getId());
        Assert.assertEquals("3", filteredLocations.get(1).getId());
    }

    @Test
    public void testFilterLocationsByAdminLevelsWithNullAdminLevelsDoesNotFilter() {
        List<Location> locations = createTestLocationList(5, true, false);

        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(locations, null, false, "");

        Assert.assertEquals(5, filteredLocations.size());
        Assert.assertEquals("0", filteredLocations.get(0).getId());
        Assert.assertEquals("1", filteredLocations.get(1).getId());
        Assert.assertEquals("2", filteredLocations.get(2).getId());
        Assert.assertEquals("3", filteredLocations.get(3).getId());
        Assert.assertEquals("4", filteredLocations.get(4).getId());
    }

    @Test
    public void testFilterLocationsByInventoryWithInventory() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle bundleWithInventory = new Bundle();
        List<Bundle.BundleEntryComponent> entriesWithInventory = new ArrayList<>();

        ListResource resource1 = new ListResource();
        resource1.setId("1");
        entriesWithInventory.add(new Bundle.BundleEntryComponent().setResource(resource1));
        bundleWithInventory.setEntry(entriesWithInventory);

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).forResource(ListResource.class);
        Mockito.doReturn(queryMock).when(queryMock).where(any(ICriterion.class));
        Mockito.doReturn(queryMock).when(queryMock).usingStyle(SearchStyleEnum.POST);
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);
        Mockito.doReturn(bundleWithInventory).when(queryMock).execute();

        List<Location> locations = createTestLocationList(5, true, false);
        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(locations, null, true, "");

        Assert.assertNotNull(filteredLocations);
        Assert.assertEquals(5, filteredLocations.size());
    }

    @Test
    public void testFilterLocationsByInventoryNoInventory() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle bundleWithInventory = new Bundle();

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).forResource(ListResource.class);
        Mockito.doReturn(queryMock).when(queryMock).where(any(ICriterion.class));
        Mockito.doReturn(queryMock).when(queryMock).usingStyle(SearchStyleEnum.POST);
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);
        Mockito.doReturn(bundleWithInventory).when(queryMock).execute();

        List<Location> locations = createTestLocationList(5, true, false);
        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(locations, null, true, "");

        Assert.assertNotNull(filteredLocations);
        Assert.assertEquals(0, filteredLocations.size());
    }

    @Test
    @SuppressWarnings("removal")
    public void testGetPaginatedLocationsSummaryReturnsSummary() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("12345").when(request).getParameter(Constants.IDENTIFIER);
        Mockito.doReturn("2").when(request).getParameter(Constants.PAGINATION_PAGE_SIZE);
        Mockito.doReturn("2").when(request).getParameter(Constants.PAGINATION_PAGE_NUMBER);
        Mockito.doReturn(Constants.COUNT).when(request).getParameter(Constants.SUMMARY);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.IDENTIFIER, new String[] {"12345"});
        parameters.put(Constants.PAGINATION_PAGE_SIZE, new String[] {"2"});
        parameters.put(Constants.PAGINATION_PAGE_NUMBER, new String[] {"2"});
        parameters.put(Constants.SUMMARY, new String[] {Constants.COUNT});

        Mockito.doReturn(parameters).when(request).getParameterMap();

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        List<Location> locations = createTestLocationList(5, false, false);
        List<String> adminLevels = new ArrayList<>();

        List<String> locationIds = Collections.singletonList("12345");

        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .adminLevelFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .lastUpdatedFilter(Mockito.any(Location.class), Mockito.any());
        Mockito.doReturn(false)
                .when(mockLocationHierarchyEndpointHelper)
                .inventoryFilter(Mockito.any(Location.class));
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocationsBackwardCompatibility(request, locationIds);
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .postFetchFilters(locations, adminLevels, false, "");

        Mockito.doReturn(locations)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchyLocations("12345", adminLevels, adminLevels, false, null);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.getPaginatedLocationsBackwardCompatibility(
                        request, locationIds);

        Assert.assertFalse(resultBundle.hasEntry());
        Assert.assertTrue(resultBundle.hasType());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(0, resultBundle.getEntry().size());
        Assert.assertEquals(5, resultBundle.getTotal());
    }

    @Test
    public void
            testHandleNonIdentifierRequestNonListModeWithSelectedLocationsReturnsLocationHierarchies() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("1,2,3,4")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"1,2,3,4"});
        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);
        MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class);

        List<Location> locations = createTestLocationList(4, false, false);
        LocationHierarchy locationHierarchy = createLocationHierarchy(locations);
        List<LocationHierarchy> locationHierarchies = new ArrayList<>();
        locationHierarchies.add(locationHierarchy);

        List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);

        Mockito.doReturn(parameters).when(request).getParameterMap();
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .extractSyncLocations("1,2,3,4");
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleNonIdentifierRequest(request, mockDecodedJWT);
        Mockito.doReturn(locationHierarchies)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchies(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
        Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                .when(mockLocationHierarchyEndpointHelper)
                .getSyncStrategyByAppId(Mockito.any());
        mockJwtUtils
                .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                .thenReturn(userRoles);
        mockJwtUtils
                .when(() -> JwtUtils.getApplicationIdFromJWT(any(DecodedJWT.class)))
                .thenReturn("test-app-id");
        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(
                        request, mockDecodedJWT);
        Assert.assertEquals(1, resultBundle.getTotal());
        Assert.assertEquals(1, resultBundle.getEntry().size());
        mockJwtUtils.close();
    }

    @Test
    public void
            testHandleNonIdentifierRequestNonListModeWithoutSelectedLocationsReturnsLocationHierarchies() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("1,2,3,4")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"1,2,3,4"});
        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        PractitionerDetailsEndpointHelper mockPractitionerDetailsEndpointHelper =
                mock(PractitionerDetailsEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);
        MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class);
        List<Location> locations = createTestLocationList(4, false, false);
        LocationHierarchy locationHierarchy = createLocationHierarchy(locations);
        List<LocationHierarchy> locationHierarchies = new ArrayList<>();
        locationHierarchies.add(locationHierarchy);
        List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);
        Mockito.doReturn(parameters).when(request).getParameterMap();
        Mockito.doReturn(Collections.emptyList())
                .when(mockLocationHierarchyEndpointHelper)
                .extractSyncLocations(Mockito.any());
        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleNonIdentifierRequest(request, mockDecodedJWT);
        Mockito.doReturn(locationHierarchies)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchies(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());
        Mockito.doReturn(Arrays.asList("1", "2", "3", "4"))
                .when(mockPractitionerDetailsEndpointHelper)
                .getPractitionerLocationIdsByKeycloakId(Mockito.any());

        Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                .when(mockLocationHierarchyEndpointHelper)
                .getSyncStrategyByAppId(Mockito.any());

        mockJwtUtils
                .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                .thenReturn(userRoles);

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(
                        request, mockDecodedJWT);

        Assert.assertEquals(1, resultBundle.getTotal());
        Assert.assertEquals(1, resultBundle.getEntry().size());
        mockJwtUtils.close();
    }

    @Test
    public void testFilterLocationsByLastUpdatedBasic() {
        List<Location> locations = createTestLocationList(5, true, true);
        String lastUpdated = OffsetDateTime.now().minusDays(2).toString();
        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(
                        locations, null, false, lastUpdated);
        Assert.assertEquals(filteredLocations.size(), 5);
        filteredLocations.forEach(
                location ->
                        Assert.assertTrue(
                                location.getMeta()
                                        .getLastUpdated()
                                        .toInstant()
                                        .isAfter(OffsetDateTime.parse(lastUpdated).toInstant())));
    }

    @Test
    public void testFilterLocationsWithNoLastUpdatedFilter() {
        List<Location> locations = createTestLocationList(5, true, true);
        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(locations, null, false, "");
        Assert.assertEquals(locations.size(), filteredLocations.size());
    }

    @Test
    public void testFilterLocationsByLastUpdatedAllFilteredOut() {
        List<Location> locations = createTestLocationList(5, true, true);
        String lastUpdated = OffsetDateTime.now().plusDays(1).toString();

        List<Location> filteredLocations =
                locationHierarchyEndpointHelper.postFetchFilters(
                        locations, null, false, lastUpdated);

        Assert.assertEquals(0, filteredLocations.size());
    }

    @Test
    public void testFetchAllDescendantsGeneratesCorrectQueryFilter() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        locationHierarchyEndpointHelper.fetchAllDescendants(
                List.of("test-parent-location-id"), List.of("4"), null);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        Assert.assertEquals(
                "Location?_tag=http://smartregister.org/CodeSystem/location-lineage%7Ctest-parent-location-id&type=https://smartregister.org/codes/administrative-level%7C4",
                result);
    }

    @Test
    public void testFetchAllDescendantsWithMultipleLocationsGeneratesCorrectQueryFilter() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        List<String> locationIds = List.of("location-1", "location-2", "location-3");
        locationHierarchyEndpointHelper.fetchAllDescendants(locationIds, List.of("4", "5"), null);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        Assert.assertEquals(
                "Location?_tag=http://smartregister.org/CodeSystem/location-lineage%7Clocation-1,http://smartregister.org/CodeSystem/location-lineage%7Clocation-2,http://smartregister.org/CodeSystem/location-lineage%7Clocation-3&type=https://smartregister.org/codes/administrative-level%7C4,https://smartregister.org/codes/administrative-level%7C5",
                result);
    }

    @Test
    public void testGetPaginatedLocationsWithMultipleLocationIds() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("_count", new String[] {"10"});
        parameterMap.put("_page", new String[] {"1"});
        parameterMap.put("filter_mode_lineage", new String[] {"true"});
        Mockito.doReturn(parameterMap).when(request).getParameterMap();
        Mockito.doReturn("10").when(request).getParameter("_count");
        Mockito.doReturn("1").when(request).getParameter("_page");
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();
        Mockito.doReturn("").when(request).getQueryString();

        List<String> locationIds = List.of("location-1", "location-2");
        List<String> adminLevels = List.of("4", "5");

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                Mockito.spy(locationHierarchyEndpointHelper);

        // Mock the fetchAllDescendantsForMultipleLocations method
        Bundle descendantsBundle = new Bundle();
        Location descendant1 = new Location();
        descendant1.setId("descendant-1");
        Location descendant2 = new Location();
        descendant2.setId("descendant-2");
        descendantsBundle.addEntry().setResource(descendant1);
        descendantsBundle.addEntry().setResource(descendant2);

        Mockito.doReturn(descendantsBundle)
                .when(mockLocationHierarchyEndpointHelper)
                .fetchAllDescendants(any(), any(), any());

        // Mock parent locations
        Bundle parentBundle = new Bundle();
        Location parent1 = new Location();
        parent1.setId("location-1");
        Location parent2 = new Location();
        parent2.setId("location-2");
        parentBundle.addEntry().setResource(parent1);
        parentBundle.addEntry().setResource(parent2);

        Mockito.doReturn(parentBundle)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationsById(locationIds);

        // Mock postFetchFilters
        List<Location> allLocations = List.of(parent1, parent2, descendant1, descendant2);
        Mockito.doReturn(allLocations)
                .when(mockLocationHierarchyEndpointHelper)
                .postFetchFilters(any(), any(), anyBoolean(), any());

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.getPaginatedLocations(
                        request, locationIds, null);

        Assert.assertNotNull(resultBundle);
        Assert.assertTrue(resultBundle.hasEntry());
        Assert.assertEquals(4, resultBundle.getEntry().size());

        // Verify that fetchAllDescendants was called with the correct parameters
        // Note: The actual admin levels will be the default range
        // [0,1,2,3,4,5,6,7,8,9,10]
        // since no specific admin levels are provided in the request
        Mockito.verify(mockLocationHierarchyEndpointHelper)
                .fetchAllDescendants(any(), any(), any());
    }

    @Test
    public void testGetLocationByIdWithCacheGeneratesCorrectUrlQueryPath() {

        Mockito.doReturn(null)
                .when(client)
                .fetchResourceFromUrl(ArgumentMatchers.any(), ArgumentMatchers.anyString());

        ArgumentCaptor<String> urlQueryPathArgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Class<IBaseResource>> classArgumentCaptor =
                ArgumentCaptor.forClass(Class.class);

        List<String> locationIds = Arrays.asList("location-test-1", "location-test-2");

        locationHierarchyEndpointHelper.getLocationsById(locationIds);

        Mockito.verify(client)
                .fetchResourceFromUrl(
                        classArgumentCaptor.capture(), urlQueryPathArgCaptor.capture());

        Class<IBaseResource> resourceType = classArgumentCaptor.getValue();
        Assert.assertNotNull(resourceType);
        Assert.assertEquals("Bundle", resourceType.getSimpleName());

        String queryPath = urlQueryPathArgCaptor.getValue();

        Assert.assertNotNull(queryPath);
        Assert.assertEquals("Location?_id=location-test-1,location-test-2", queryPath);
    }

    @Test
    public void testHandleIdentifierRequestNoListModePaginatesLocations() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        Mockito.doReturn("1,2,3,4")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);

        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();

        parameters.put(Constants.FILTER_MODE_LINEAGE, new String[] {"true"});
        parameters.put(Constants.LAST_UPDATED, new String[] {"2025-13-05"});

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);

        Mockito.doReturn(parameters).when(request).getParameterMap();

        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleIdentifierRequest(request, "test-location-id");

        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleIdentifierRequest(request, "test-location-id");

        LocationHierarchy locationHierarchy = new LocationHierarchy();
        locationHierarchy.setLocationId(new StringType("test-location-id"));

        Mockito.doReturn(locationHierarchy)
                .when(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchy(
                        Mockito.anyString(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any());

        Bundle resultBundle =
                mockLocationHierarchyEndpointHelper.handleIdentifierRequest(
                        request, "test-location-id");

        Mockito.verify(mockLocationHierarchyEndpointHelper)
                .getLocationHierarchy(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());

        Assert.assertTrue(resultBundle.hasTotal());
        Assert.assertEquals(1, resultBundle.getTotal());
        Assert.assertEquals(1, resultBundle.getEntry().size());

        IBaseResource resource = resultBundle.getEntryFirstRep().getResource();

        Assert.assertTrue(resource instanceof LocationHierarchy);
        Assert.assertEquals(
                new StringType("test-location-id").getValueAsString(),
                ((LocationHierarchy) resource).getLocationId().getValueAsString());
    }

    @Test
    public void testHandleIdentifierRequestListModePaginatesLocations() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        Mockito.doReturn("1,2,3,4")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);

        Mockito.doReturn("list").when(request).getParameter(Constants.MODE);

        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();

        parameters.put(Constants.FILTER_MODE_LINEAGE, new String[] {"true"});
        parameters.put(Constants.MODE, new String[] {"list"});
        parameters.put(Constants.LAST_UPDATED, new String[] {"2025-13-05"});

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);

        Mockito.doReturn(parameters).when(request).getParameterMap();

        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleIdentifierRequest(request, "test-location-id");

        Mockito.doCallRealMethod()
                .when(mockLocationHierarchyEndpointHelper)
                .handleIdentifierRequest(request, "test-location-id");

        List<Location> locations = createTestLocationList(1, false, false);

        Mockito.doReturn(Utils.createBundle(locations))
                .when(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocations(Mockito.any(), Mockito.any(), Mockito.any());

        mockLocationHierarchyEndpointHelper.handleIdentifierRequest(request, "test-location-id");

        ArgumentCaptor<List<String>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);

        Mockito.verify(mockLocationHierarchyEndpointHelper)
                .getPaginatedLocations(
                        ArgumentMatchers.any(HttpServletRequest.class),
                        listArgumentCaptor.capture(),
                        ArgumentMatchers.any());

        List<String> locationIds = listArgumentCaptor.getValue();

        Assert.assertNotNull(locationIds);
        Assert.assertEquals(1, locationIds.size());
        Assert.assertEquals("test-location-id", locationIds.get(0));
    }

    @Test
    public void testFetchAllDescendantsUsesRelatedEntityLocationTag() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        String relatedEntityTagUrl = Constants.DEFAULT_RELATED_ENTITY_TAG_URL;
        locationHierarchyEndpointHelper.fetchAllDescendants(
                List.of("test-location-id"), List.of("4"), relatedEntityTagUrl);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        // Verify that both RELATED_ENTITY_LOCATION tag and location-lineage tag are used
        // to match locations with either tag system
        Assert.assertTrue(
                "Query should contain RELATED_ENTITY_LOCATION tag",
                result.contains(relatedEntityTagUrl));
        Assert.assertTrue(
                "Query should contain location-lineage tag",
                result.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
        // Verify that both tag systems are present with the location ID
        // The format should be:
        // _tag=RELATED_ENTITY_TAG_URL%7Ctest-location-id,SYSTEM_LOCATION_HIERARCHY%7Ctest-location-id
        String expectedRelatedEntityTag = relatedEntityTagUrl + "%7Ctest-location-id";
        String expectedLocationLineageTag =
                Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY + "%7Ctest-location-id";
        Assert.assertTrue(
                "Query should contain RELATED_ENTITY_LOCATION tag with location ID",
                result.contains(expectedRelatedEntityTag));
        Assert.assertTrue(
                "Query should contain location-lineage tag with location ID",
                result.contains(expectedLocationLineageTag));
        // Verify the tags are separated by comma (OR logic in FHIR)
        int relatedEntityIndex = result.indexOf(expectedRelatedEntityTag);
        int locationLineageIndex = result.indexOf(expectedLocationLineageTag);
        Assert.assertTrue(
                "Both tag systems should be present in query",
                relatedEntityIndex > 0 && locationLineageIndex > 0);
        // Verify the admin level filter is present
        Assert.assertTrue(
                "Query should contain admin level filter",
                result.contains("type=https://smartregister.org/codes/administrative-level%7C4"));
    }

    @Test
    public void testFetchAllDescendantsUsesDefaultTagWhenTagUrlIsNull() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        locationHierarchyEndpointHelper.fetchAllDescendants(
                List.of("test-location-id"), List.of("4"), null);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        // Verify that the default location hierarchy tag is used when tagUrl is null
        Assert.assertTrue(result.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
        Assert.assertFalse(result.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));
    }

    @Test
    public void testGetPaginatedLocationsRespectsAdminLevelRange() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("10").when(request).getParameter(Constants.PAGINATION_PAGE_SIZE);
        Mockito.doReturn("1").when(request).getParameter(Constants.PAGINATION_PAGE_NUMBER);
        Mockito.doReturn("4").when(request).getParameter(Constants.MIN_ADMIN_LEVEL);
        Mockito.doReturn("4").when(request).getParameter(Constants.MAX_ADMIN_LEVEL);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put(Constants.MIN_ADMIN_LEVEL, new String[] {"4"});
        parameterMap.put(Constants.MAX_ADMIN_LEVEL, new String[] {"4"});
        Mockito.doReturn(parameterMap).when(request).getParameterMap();
        Mockito.doReturn("").when(request).getQueryString();

        LocationHierarchyEndpointHelper helperSpy = Mockito.spy(locationHierarchyEndpointHelper);

        List<Location> descendantLocations = createTestLocationList(6, true, false);
        Bundle descendantsBundle = Utils.createBundle(descendantLocations);
        Mockito.doReturn(descendantsBundle)
                .when(helperSpy)
                .fetchAllDescendants(Mockito.anyList(), Mockito.anyList(), Mockito.any());
        Mockito.doReturn(Utils.createBundle(Collections.emptyList()))
                .when(helperSpy)
                .getLocationsById(Mockito.anyList());

        Bundle resultBundle =
                helperSpy.getPaginatedLocations(request, Collections.singletonList("parent"), null);

        Assert.assertNotNull(resultBundle);
        Assert.assertEquals(1, resultBundle.getEntry().size());
        Location filteredLocation = (Location) resultBundle.getEntryFirstRep().getResource();
        Assert.assertEquals("4", filteredLocation.getTypeFirstRep().getCodingFirstRep().getCode());

        Mockito.verify(helperSpy)
                .fetchAllDescendants(
                        Mockito.anyList(),
                        Mockito.eq(Collections.singletonList("4")),
                        Mockito.any());
    }

    @Test
    public void
            testHandleNonIdentifierRequestWithSyncLocationsUsesRelatedEntityLocationTagForListMode() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("list").when(request).getParameter(Constants.MODE);
        Mockito.doReturn("loc1,loc2")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.MODE, new String[] {"list"});
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"loc1,loc2"});
        parameters.put(Constants.FILTER_MODE_LINEAGE, new String[] {"true"});

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);

        // Use try-with-resources to ensure static mock is properly closed
        try (MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class)) {

            List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);
            List<String> selectedSyncLocations = List.of("loc1", "loc2");

            Mockito.doReturn(parameters).when(request).getParameterMap();
            Mockito.doCallRealMethod()
                    .when(mockLocationHierarchyEndpointHelper)
                    .extractSyncLocations("loc1,loc2");
            Mockito.doCallRealMethod()
                    .when(mockLocationHierarchyEndpointHelper)
                    .handleNonIdentifierRequest(request, mockDecodedJWT);
            Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getSyncStrategyByAppId(Mockito.any());

            mockJwtUtils
                    .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                    .thenReturn(userRoles);
            mockJwtUtils
                    .when(() -> JwtUtils.getApplicationIdFromJWT(any(DecodedJWT.class)))
                    .thenReturn("test-app-id");

            Bundle mockBundle = new Bundle();
            Mockito.doReturn(mockBundle)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getPaginatedLocations(
                            Mockito.any(HttpServletRequest.class),
                            Mockito.eq(selectedSyncLocations),
                            Mockito.anyString());

            mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(request, mockDecodedJWT);

            // Verify that getPaginatedLocations was called with RELATED_ENTITY_LOCATION tag URL
            ArgumentCaptor<String> tagUrlCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(mockLocationHierarchyEndpointHelper)
                    .getPaginatedLocations(
                            Mockito.any(HttpServletRequest.class),
                            Mockito.eq(selectedSyncLocations),
                            tagUrlCaptor.capture());

            String capturedTagUrl = tagUrlCaptor.getValue();
            Assert.assertNotNull(capturedTagUrl);
            Assert.assertEquals(Constants.DEFAULT_RELATED_ENTITY_TAG_URL, capturedTagUrl);
        }
    }

    @Test
    public void
            testHandleNonIdentifierRequestWithSyncLocationsUsesRelatedEntityLocationTagForNonListMode() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Mockito.doReturn("loc1,loc2")
                .when(request)
                .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                .when(request)
                .getRequestURL();

        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"loc1,loc2"});

        LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                mock(LocationHierarchyEndpointHelper.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);

        // Use try-with-resources to ensure static mock is properly closed
        try (MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class)) {

            List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);
            List<String> selectedSyncLocations = List.of("loc1", "loc2");
            List<LocationHierarchy> locationHierarchies = new ArrayList<>();
            LocationHierarchy locationHierarchy =
                    createLocationHierarchy(createTestLocationList(2, false, false));
            locationHierarchies.add(locationHierarchy);

            Mockito.doReturn(parameters).when(request).getParameterMap();
            Mockito.doCallRealMethod()
                    .when(mockLocationHierarchyEndpointHelper)
                    .extractSyncLocations("loc1,loc2");
            Mockito.doCallRealMethod()
                    .when(mockLocationHierarchyEndpointHelper)
                    .handleNonIdentifierRequest(request, mockDecodedJWT);
            Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getSyncStrategyByAppId(Mockito.any());
            Mockito.doReturn(locationHierarchies)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getLocationHierarchies(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.anyString());

            mockJwtUtils
                    .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                    .thenReturn(userRoles);
            mockJwtUtils
                    .when(() -> JwtUtils.getApplicationIdFromJWT(any(DecodedJWT.class)))
                    .thenReturn("test-app-id");

            mockLocationHierarchyEndpointHelper.handleNonIdentifierRequest(request, mockDecodedJWT);

            // Verify that getLocationHierarchies was called with RELATED_ENTITY_LOCATION tag URL
            ArgumentCaptor<String> tagUrlCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(mockLocationHierarchyEndpointHelper)
                    .getLocationHierarchies(
                            Mockito.eq(selectedSyncLocations),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.any(),
                            tagUrlCaptor.capture());

            String capturedTagUrl = tagUrlCaptor.getValue();
            Assert.assertNotNull(capturedTagUrl);
            Assert.assertEquals(Constants.DEFAULT_RELATED_ENTITY_TAG_URL, capturedTagUrl);
        }
    }

    @Test
    public void testStreamPaginatedLocationsUsesRelatedEntityLocationTagWithSyncLocations()
            throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse response =
                mock(jakarta.servlet.http.HttpServletResponse.class);
        DecodedJWT mockDecodedJWT = mock(DecodedJWT.class);

        // Use try-with-resources to ensure static mock is properly closed
        try (MockedStatic<JwtUtils> mockJwtUtils = Mockito.mockStatic(JwtUtils.class)) {

            Mockito.doReturn("10").when(request).getParameter(Constants.PAGINATION_PAGE_SIZE);
            Mockito.doReturn("1").when(request).getParameter(Constants.PAGINATION_PAGE_NUMBER);
            Mockito.doReturn("loc1,loc2")
                    .when(request)
                    .getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
            Mockito.doReturn(new StringBuffer("http://test:8080/LocationHierarchy"))
                    .when(request)
                    .getRequestURL();
            Mockito.doReturn("").when(request).getQueryString();

            Map<String, String[]> parameters = new HashMap<>();
            parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"loc1,loc2"});
            Mockito.doReturn(parameters).when(request).getParameterMap();

            List<String> locationIds = List.of("loc1", "loc2");
            List<String> userRoles = Collections.singletonList(Constants.ROLE_ALL_LOCATIONS);

            LocationHierarchyEndpointHelper mockLocationHierarchyEndpointHelper =
                    Mockito.spy(locationHierarchyEndpointHelper);

            mockJwtUtils
                    .when(() -> JwtUtils.getUserRolesFromJWT(any(DecodedJWT.class)))
                    .thenReturn(userRoles);
            mockJwtUtils
                    .when(() -> JwtUtils.getApplicationIdFromJWT(any(DecodedJWT.class)))
                    .thenReturn("test-app-id");

            Mockito.doReturn(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getSyncStrategyByAppId(Mockito.any());

            Bundle mockBundle = new Bundle();
            // Add enough entries to trigger streaming (threshold is 1000)
            List<Location> mockLocations = new ArrayList<>();
            for (int i = 0; i < 1001; i++) {
                Location loc = new Location();
                loc.setId("loc-" + i);
                mockBundle.addEntry().setResource(loc);
                mockLocations.add(loc);
            }
            Mockito.doReturn(mockBundle)
                    .when(mockLocationHierarchyEndpointHelper)
                    .fetchAllDescendants(Mockito.any(), Mockito.any(), Mockito.anyString());

            // Mock getLocationsById to return parent locations
            Bundle parentBundle = new Bundle();
            Location parent1 = new Location();
            parent1.setId("loc1");
            Location parent2 = new Location();
            parent2.setId("loc2");
            parentBundle.addEntry().setResource(parent1);
            parentBundle.addEntry().setResource(parent2);
            mockLocations.add(parent1);
            mockLocations.add(parent2);
            Mockito.doReturn(parentBundle)
                    .when(mockLocationHierarchyEndpointHelper)
                    .getLocationsById(Mockito.any());

            // Mock postFetchFilters to return all locations (to trigger streaming)
            // With 1001+ locations, streaming mode will be used
            Mockito.doReturn(mockLocations)
                    .when(mockLocationHierarchyEndpointHelper)
                    .postFetchFilters(
                            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any());

            try {
                mockLocationHierarchyEndpointHelper.streamPaginatedLocations(
                        request, response, locationIds, mockDecodedJWT);
            } catch (Exception e) {
                // Expected to fail due to mocking, but we can verify the tag URL was used
            }

            // Verify that fetchAllDescendants was called with RELATED_ENTITY_LOCATION tag URL
            // Note: It may be called multiple times (from streamPaginatedLocations and potentially
            // getPaginatedLocations), so we verify at least once
            ArgumentCaptor<String> tagUrlCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(mockLocationHierarchyEndpointHelper, Mockito.atLeastOnce())
                    .fetchAllDescendants(
                            Mockito.eq(locationIds), Mockito.any(), tagUrlCaptor.capture());

            // Get the last captured tag URL (or first if only one)
            List<String> capturedTagUrls = tagUrlCaptor.getAllValues();
            Assert.assertFalse(
                    "Should have captured at least one tag URL", capturedTagUrls.isEmpty());
            String capturedTagUrl = capturedTagUrls.get(capturedTagUrls.size() - 1);
            Assert.assertNotNull(capturedTagUrl);
            Assert.assertEquals(Constants.DEFAULT_RELATED_ENTITY_TAG_URL, capturedTagUrl);
        }
    }

    @Test
    public void testFetchAllDescendantsWithEmptyLocationIds() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        Bundle result =
                locationHierarchyEndpointHelper.fetchAllDescendants(
                        Collections.emptyList(), List.of("4"), null);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String queryString = argCaptor.getAllValues().get(0);

        // Should only contain admin level filter, no tag filter
        Assert.assertTrue(queryString.contains("type="));
        Assert.assertFalse(queryString.contains("_tag="));
    }

    @Test
    public void testFetchAllDescendantsWithNullTagUrlUsesLocationLineageTag() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        locationHierarchyEndpointHelper.fetchAllDescendants(
                List.of("test-location-id"), List.of("4"), null);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        // Should use location-lineage tag when tagUrl is null
        Assert.assertTrue(result.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
        Assert.assertFalse(result.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));
    }

    @Test
    public void testFetchLocationChunkUsesBothTagSystemsForRelatedEntityLocation() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle bundleMock = new Bundle();
        bundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);
        Mockito.doReturn(bundleMock).when(queryMock).execute();

        LocationHierarchyEndpointHelper spyHelper = Mockito.spy(locationHierarchyEndpointHelper);

        // Use reflection to call private method
        try {
            java.lang.reflect.Method method =
                    LocationHierarchyEndpointHelper.class.getDeclaredMethod(
                            "fetchLocationChunk",
                            List.class,
                            List.class,
                            List.class,
                            Boolean.class,
                            String.class,
                            int.class,
                            int.class,
                            String.class);
            method.setAccessible(true);
            method.invoke(
                    spyHelper,
                    List.of("test-location-id"),
                    List.of("4"),
                    Collections.emptyList(),
                    false,
                    null,
                    0,
                    10,
                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL);

            ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
            String result = argCaptor.getAllValues().get(0);

            Assert.assertNotNull(result);
            // Should contain both tag systems
            Assert.assertTrue(result.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));
            Assert.assertTrue(result.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
            // Should contain both tag parameters with location ID
            Assert.assertTrue(
                    result.contains(
                            Constants.DEFAULT_RELATED_ENTITY_TAG_URL + "%7Ctest-location-id"));
            Assert.assertTrue(
                    result.contains(
                            Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY + "%7Ctest-location-id"));
        } catch (Exception e) {
            Assert.fail("Failed to test fetchLocationChunk: " + e.getMessage());
        }
    }

    @Test
    public void testGetLocationCountUsesBothTagSystemsForRelatedEntityLocation() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle bundleMock = new Bundle();
        bundleMock.setTotal(5);

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);
        Mockito.doReturn(bundleMock).when(queryMock).execute();

        LocationHierarchyEndpointHelper spyHelper = Mockito.spy(locationHierarchyEndpointHelper);

        // Use reflection to call private method
        try {
            java.lang.reflect.Method method =
                    LocationHierarchyEndpointHelper.class.getDeclaredMethod(
                            "getLocationCount",
                            List.class,
                            List.class,
                            List.class,
                            Boolean.class,
                            String.class,
                            String.class);
            method.setAccessible(true);
            int count =
                    (int)
                            method.invoke(
                                    spyHelper,
                                    List.of("test-location-id"),
                                    List.of("4"),
                                    Collections.emptyList(),
                                    false,
                                    null,
                                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL);

            ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
            String result = argCaptor.getAllValues().get(0);

            Assert.assertNotNull(result);
            // Should contain both tag systems
            Assert.assertTrue(result.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));
            Assert.assertTrue(result.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
            // Should contain _count=0 for count query
            Assert.assertTrue(result.contains("_count=0"));
        } catch (Exception e) {
            Assert.fail("Failed to test getLocationCount: " + e.getMessage());
        }
    }

    @Test
    public void testFetchAllDescendantsWithMultipleLocationIds() {
        IUntypedQuery<IBaseBundle> untypedQueryMock = mock(IUntypedQuery.class);
        IQuery<IBaseBundle> queryMock = mock(IQuery.class);

        Bundle secondBundleMock = new Bundle();
        secondBundleMock.setEntry(new ArrayList<>());

        Mockito.doReturn(untypedQueryMock).when(client).search();
        Mockito.doReturn(queryMock).when(untypedQueryMock).byUrl(anyString());
        Mockito.doReturn(queryMock).when(queryMock).returnBundle(Bundle.class);

        List<String> locationIds = List.of("loc1", "loc2", "loc3");
        locationHierarchyEndpointHelper.fetchAllDescendants(
                locationIds, List.of("4"), Constants.DEFAULT_RELATED_ENTITY_TAG_URL);

        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(untypedQueryMock, Mockito.atLeastOnce()).byUrl(argCaptor.capture());
        String result = argCaptor.getAllValues().get(0);

        Assert.assertNotNull(result);
        // Should contain all location IDs for both tag systems
        for (String locationId : locationIds) {
            String relatedEntityTagPattern =
                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL + "%7C" + locationId;
            String locationLineageTagPattern =
                    Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY + "%7C" + locationId;

            Assert.assertTrue(
                    "Should contain RELATED_ENTITY_LOCATION tag for " + locationId,
                    result.contains(relatedEntityTagPattern));
            Assert.assertTrue(
                    "Should contain location-lineage tag for " + locationId,
                    result.contains(locationLineageTagPattern));
        }
    }

    @Test
    public void testBuildCommaSeparatedValuesWithEmptyList() {
        LocationHierarchyEndpointHelper spyHelper = Mockito.spy(locationHierarchyEndpointHelper);

        try {
            java.lang.reflect.Method method =
                    LocationHierarchyEndpointHelper.class.getDeclaredMethod(
                            "buildCommaSeparatedValues", List.class, String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(spyHelper, Collections.emptyList(), "prefix");

            Assert.assertEquals("", result);
        } catch (Exception e) {
            Assert.fail("Failed to test buildCommaSeparatedValues: " + e.getMessage());
        }
    }

    @Test
    public void testBuildCommaSeparatedValuesWithNullList() {
        LocationHierarchyEndpointHelper spyHelper = Mockito.spy(locationHierarchyEndpointHelper);

        try {
            java.lang.reflect.Method method =
                    LocationHierarchyEndpointHelper.class.getDeclaredMethod(
                            "buildCommaSeparatedValues", List.class, String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(spyHelper, null, "prefix");

            Assert.assertEquals("", result);
        } catch (Exception e) {
            Assert.fail("Failed to test buildCommaSeparatedValues with null: " + e.getMessage());
        }
    }

    @Test
    public void testBuildCommaSeparatedValuesWithBlankValues() {
        LocationHierarchyEndpointHelper spyHelper = Mockito.spy(locationHierarchyEndpointHelper);

        try {
            java.lang.reflect.Method method =
                    LocationHierarchyEndpointHelper.class.getDeclaredMethod(
                            "buildCommaSeparatedValues", List.class, String.class);
            method.setAccessible(true);
            String result =
                    (String)
                            method.invoke(
                                    spyHelper,
                                    Arrays.asList("valid", "", "  ", null, "valid2"),
                                    "prefix");

            // Should only contain non-blank values
            Assert.assertTrue(result.contains("prefix%7Cvalid"));
            Assert.assertTrue(result.contains("prefix%7Cvalid2"));
            // Should not contain just "prefix%7C" without a value (but can contain it as part of
            // valid values)
            // Check that it doesn't have a standalone "prefix%7C" followed by comma or end of
            // string
            Assert.assertFalse(
                    "Result should not contain standalone prefix%7C",
                    result.matches(".*prefix%7C[,]?.*") && !result.contains("prefix%7Cvalid"));
            // Should not have trailing comma
            Assert.assertFalse(result.endsWith(","));
            // Verify the exact format: should be comma-separated values
            String[] parts = result.split(",");
            Assert.assertEquals("Should have exactly 2 valid values", 2, parts.length);
            Assert.assertTrue(
                    "First part should be prefix%7Cvalid", parts[0].equals("prefix%7Cvalid"));
            Assert.assertTrue(
                    "Second part should be prefix%7Cvalid2", parts[1].equals("prefix%7Cvalid2"));
        } catch (Exception e) {
            Assert.fail(
                    "Failed to test buildCommaSeparatedValues with blank values: "
                            + e.getMessage());
        }
    }
}
