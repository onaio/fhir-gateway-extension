package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.utils.Constants.LOCATION_RESOURCE;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import com.google.fhir.gateway.ExceptionUtil;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
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

    public LocationHierarchy getLocationHierarchy(String locationId, List<String> adminLevels) {
        LocationHierarchy locationHierarchy;

        if (CacheHelper.INSTANCE.skipCache()) {
            locationHierarchy = getLocationHierarchyCore(locationId, adminLevels);
        } else {
            locationHierarchy =
                    (LocationHierarchy)
                            CacheHelper.INSTANCE.resourceCache.get(
                                    locationId,
                                    key -> getLocationHierarchyCore(locationId, adminLevels));
        }
        return locationHierarchy;
    }

    public LocationHierarchy getLocationHierarchyCore(String locationId, List<String> adminLevels) {
        Location location = getLocationById(locationId);

        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        if (location != null) {
            logger.info("Building Location Hierarchy of Location Id : " + locationId);
            locationHierarchyTree.buildTreeFromList(
                    getDescendants(locationId, location, adminLevels));
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

    private List<Location> getLocationHierarchyLocations(
            String locationId, Location parentLocation, List<String> adminLevels) {
        List<Location> descendants;

        if (CacheHelper.INSTANCE.skipCache()) {
            descendants = getDescendants(locationId, parentLocation, adminLevels);
        } else {
            descendants =
                    CacheHelper.INSTANCE.locationListCache.get(
                            locationId,
                            key -> getDescendants(locationId, parentLocation, adminLevels));
        }
        return descendants;
    }

    public List<Location> getDescendants(
            String locationId, Location parentLocation, List<String> adminLevels) {
        IQuery<IBaseBundle> query =
                getFhirClientForR4()
                        .search()
                        .forResource(Location.class)
                        .where(
                                new ReferenceClientParam(Location.SP_PARTOF)
                                        .hasAnyOfIds(locationId));

        if (adminLevels != null && !adminLevels.isEmpty()) {
            TokenClientParam adminLevelParam = new TokenClientParam(Constants.TYPE_SEARCH_PARAM);
            String[] adminLevelArray = adminLevels.toArray(new String[0]);

            query =
                    query.and(
                            adminLevelParam
                                    .exactly()
                                    .systemAndValues(
                                            Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL,
                                            adminLevelArray));
        }

        Bundle childLocationBundle = query.returnBundle(Bundle.class).execute();

        List<Location> allLocations = new ArrayList<>();
        if (parentLocation != null) {
            allLocations.add(parentLocation);
        }

        if (childLocationBundle != null) {
            for (Bundle.BundleEntryComponent childLocation : childLocationBundle.getEntry()) {
                Location childLocationEntity = (Location) childLocation.getResource();
                allLocations.add(childLocationEntity);
                allLocations.addAll(
                        getDescendants(
                                childLocationEntity.getIdElement().getIdPart(), null, adminLevels));
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
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        List<String> adminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);
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
        List<Location> locations =
                getLocationHierarchyLocations(identifier, parentLocation, adminLevels);
        List<Resource> resourceLocations = new ArrayList<>(locations);
        int totalEntries = locations.size();

        int end = Math.min(start + count, resourceLocations.size());
        List<Resource> paginatedResourceLocations = resourceLocations.subList(start, end);
        Bundle resultBundle;

        if (locations.isEmpty()) {
            resultBundle =
                    Utils.createEmptyBundle(
                            request.getRequestURL() + "?" + request.getQueryString());
        } else {
            resultBundle = Utils.createBundle(paginatedResourceLocations);
            StringBuilder urlBuilder = new StringBuilder(request.getRequestURL());
            Utils.addPaginationLinks(
                    urlBuilder, resultBundle, page, totalEntries, count, parameters);
        }

        return resultBundle;
    }

    public List<String> generateAdminLevels(
            String administrativeLevelMin, String administrativeLevelMax) {
        List<String> adminLevels = new ArrayList<>();

        int max = Constants.DEFAULT_MAX_ADMIN_LEVEL;

        if (administrativeLevelMin != null && !administrativeLevelMin.isEmpty()) {
            int min = Integer.parseInt(administrativeLevelMin);

            if (administrativeLevelMax != null && !administrativeLevelMax.isEmpty()) {
                int maxLevel = Integer.parseInt(administrativeLevelMax);
                if (min > maxLevel) {
                    ForbiddenOperationException forbiddenOperationException =
                            new ForbiddenOperationException(
                                    "administrativeLevelMin cannot be greater than"
                                            + " administrativeLevelMax");
                    ExceptionUtil.throwRuntimeExceptionAndLog(
                            logger,
                            forbiddenOperationException.getMessage(),
                            forbiddenOperationException);
                }
                for (int i = min; i <= maxLevel; i++) {
                    adminLevels.add(String.valueOf(i));
                }
            } else {
                for (int i = min; i <= max; i++) {
                    adminLevels.add(String.valueOf(i));
                }
            }
        } else if (administrativeLevelMax != null && !administrativeLevelMax.isEmpty()) {
            int maxLevel = Integer.parseInt(administrativeLevelMax);
            for (int i = 0; i <= maxLevel; i++) {
                adminLevels.add(String.valueOf(i));
            }
        }

        return adminLevels;
    }
}
