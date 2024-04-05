package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.utils.Constants.LOCATION_RESOURCE;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

public class LocationHierarchyEndpointHelper {

    private static final Logger logger =
            LoggerFactory.getLogger(LocationHierarchyEndpointHelper.class);

    private final IGenericClient r4FHIRClient;

    public LocationHierarchyEndpointHelper(IGenericClient fhirClient) {
        this.r4FHIRClient = fhirClient;
    }

    private IGenericClient getFhirClientForR4() {
        return r4FHIRClient;
    }

    public LocationHierarchy getLocationHierarchy(String locationId) {
        LocationHierarchy locationHierarchy;

        if (CacheHelper.INSTANCE.skipCache()) {
            locationHierarchy = getLocationHierarchyCore(locationId);
        } else {
            locationHierarchy =
                    (LocationHierarchy)
                            CacheHelper.INSTANCE.resourceCache.get(
                                    locationId, this::getLocationHierarchyCore);
        }
        return locationHierarchy;
    }

    public LocationHierarchy getLocationHierarchyCore(String locationId) {
        Location location = getLocationById(locationId);

        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        if (location != null) {
            logger.info("Building Location Hierarchy of Location Id : " + locationId);
            locationHierarchyTree.buildTreeFromList(getDescendants(locationId, location));
            StringType locationIdString = new StringType().setId(locationId).getIdElement();
            locationHierarchy.setLocationId(locationIdString);
            locationHierarchy.setId(LOCATION_RESOURCE + locationId);

            locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
        } else {
            logger.error("LocationHierarchy with identifier: " + locationId + " not found");
            locationHierarchy.setId(LOCATION_RESOURCE_NOT_FOUND);
        }
        return locationHierarchy;
    }

    private List<Location> getLocationHierarchy(String locationId, Location parentLocation) {
        return getDescendants(locationId, parentLocation);
    }

    public List<Location> getDescendants(String locationId, Location parentLocation) {

        Bundle childLocationBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(Location.class)
                        .where(new ReferenceClientParam(Location.SP_PARTOF).hasAnyOfIds(locationId))
                        .returnBundle(Bundle.class)
                        .execute();

        List<Location> allLocations = new ArrayList<>();
        if (parentLocation != null) {
            allLocations.add(parentLocation);
        }

        if (childLocationBundle != null) {
            for (Bundle.BundleEntryComponent childLocation : childLocationBundle.getEntry()) {
                Location childLocationEntity = (Location) childLocation.getResource();
                allLocations.add(childLocationEntity);
                allLocations.addAll(
                        getDescendants(childLocationEntity.getIdElement().getIdPart(), null));
            }
        }

        return allLocations;
    }

    public @Nullable Location getLocationById(String locationId) {
        Location location = null;
        try {
            location =
                    getFhirClientForR4()
                            .fetchResourceFromUrl(Location.class, "Location/" + locationId);
        } catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
        }
        return location;
    }

    public Bundle getPaginatedLocations(HttpServletRequest request) {
        String identifier = request.getParameter(Constants.IDENTIFIER);
        String pageSize = request.getParameter(Constants.PAGINATION_PAGE_SIZE);
        String pageNumber = request.getParameter(Constants.PAGINATION_PAGE_NUMBER);
        Map<String, String[]> parameters = new HashMap<>(request.getParameterMap());

        int count =
                pageSize != null
                        ? Integer.parseInt(pageSize)
                        : Constants.PAGINATION_DEFAULT_PAGE_SIZE;
        int page =
                pageNumber != null
                        ? Integer.parseInt(pageNumber)
                        : Constants.PAGINATION_DEFAULT_PAGE_NUMBER;

        int start = Math.max(0, (page - 1)) * count;
        Location parentLocation = getLocationById(identifier);
        List<Location> locations = getDescendants(identifier, parentLocation);
        List<Resource> resourceLocations = new ArrayList<>(locations);
        int totalEntries = locations.size();

        int end = Math.min(start + count, resourceLocations.size());
        List<Resource> paginatedResourceLocations = resourceLocations.subList(start, end);
        Bundle resultBundle;

        if (locations.isEmpty()) {
            resultBundle =
                    BaseEndpoint.createEmptyBundle(
                            request.getRequestURL() + "?" + request.getQueryString());
        } else {
            resultBundle = BaseEndpoint.createBundle(paginatedResourceLocations);
            StringBuilder urlBuilder = new StringBuilder(request.getRequestURL());
            SyncAccessDecision.addPaginationLinks(
                    urlBuilder, resultBundle, page, totalEntries, count, parameters);
        }

        return resultBundle;
    }
}
