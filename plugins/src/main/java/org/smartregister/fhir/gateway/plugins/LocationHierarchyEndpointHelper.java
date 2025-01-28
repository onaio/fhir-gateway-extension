package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.utils.Constants.LOCATION_RESOURCE;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.fhir.gateway.ExceptionUtil;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;

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

    public LocationHierarchy getLocationHierarchy(
            String locationId,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated) {
        // TODO: implement correct caching
        return getLocationHierarchyCore(
                locationId,
                preFetchAdminLevels,
                postFetchAdminLevels,
                filterInventory,
                lastUpdated);
    }

    public List<LocationHierarchy> getLocationHierarchies(
            List<String> locationIds,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated) {

        locationIds = locationIds != null ? locationIds : Collections.emptyList();

        return locationIds.parallelStream()
                .map(
                        locationId ->
                                getLocationHierarchy(
                                        locationId,
                                        preFetchAdminLevels,
                                        postFetchAdminLevels,
                                        filterInventory,
                                        lastUpdated))
                .collect(Collectors.toList());
    }

    public LocationHierarchy getLocationHierarchyCore(
            String locationId,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated) {
        Location location = getLocationById(locationId);

        LocationHierarchyTree locationHierarchyTree = new LocationHierarchyTree();
        LocationHierarchy locationHierarchy = new LocationHierarchy();
        if (location != null) {
            logger.info("Building Location Hierarchy of Location Id : {}", locationId);

            List<Location> descendants = getDescendants(locationId, location, preFetchAdminLevels);

            descendants =
                    postFetchFilters(
                            descendants, postFetchAdminLevels, filterInventory, lastUpdated);

            locationHierarchyTree.buildTreeFromList(descendants);
            StringType locationIdString = new StringType().setId(locationId).getIdElement();
            locationHierarchy.setLocationId(locationIdString);
            locationHierarchy.setId(LOCATION_RESOURCE + locationId);

            locationHierarchy.setLocationHierarchyTree(locationHierarchyTree);
        } else {
            logger.error("LocationHierarchy with identifier: {} not found", locationId);
            locationHierarchy.setId(LOCATION_RESOURCE_NOT_FOUND);
        }
        return locationHierarchy;
    }

    public List<Location> getLocationHierarchyLocations(
            String locationId,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated) {

        List<Location> descendants;
        Location parentLocation = getLocationById(locationId);

        if (CacheHelper.INSTANCE.skipCache()) {
            descendants = getDescendants(locationId, parentLocation, preFetchAdminLevels);
        } else {
            descendants =
                    CacheHelper.INSTANCE.locationListCache.get(
                            locationId,
                            key -> getDescendants(locationId, parentLocation, preFetchAdminLevels));
        }
        descendants =
                postFetchFilters(descendants, postFetchAdminLevels, filterInventory, lastUpdated);
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

        Bundle childLocationBundle =
                query.usingStyle(SearchStyleEnum.POST)
                        .count(
                                SyncAccessDecision.SyncAccessDecisionConstants
                                        .REL_LOCATION_CHUNK_SIZE)
                        .returnBundle(Bundle.class)
                        .execute();

        List<Location> allLocations = Collections.synchronizedList(new ArrayList<>());
        if (parentLocation != null) {
            allLocations.add(parentLocation);
        }
        if (childLocationBundle != null) {
            Utils.fetchAllBundlePagesAndInject(r4FHIRClient, childLocationBundle);
            childLocationBundle.getEntry().parallelStream()
                    .forEach(
                            childLocation -> {
                                Location childLocationEntity =
                                        (Location) childLocation.getResource();
                                allLocations.add(childLocationEntity);
                                allLocations.addAll(
                                        getDescendants(
                                                childLocationEntity.getIdElement().getIdPart(),
                                                null,
                                                adminLevels));
                            });
        }

        return allLocations;
    }

    public @Nullable Location getLocationById(String locationId) {
        Location location = null;
        try {
            location =
                    getFhirClientForR4()
                            .fetchResourceFromUrl(
                                    Location.class,
                                    "Location/"
                                            + Utils.extractLogicalId(
                                                    ResourceType.Location, locationId));
        } catch (ResourceNotFoundException e) {
            logger.error(e.getMessage());
        }
        return location;
    }

    public @Nullable Bundle getLocationById(List<String> ids) {
        return getFhirClientForR4()
                .fetchResourceFromUrl(Bundle.class, "Location?_id=" + StringUtils.join(ids, ","));
    }

    public Bundle handleIdentifierRequest(HttpServletRequest request, String identifier) {
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        String mode = request.getParameter(Constants.MODE);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        String filterMode = request.getParameter(Constants.LOCATION_FILTER_MODE);
        String lastUpdated = "";
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);
        if (Constants.LIST.equals(mode)) {
            List<String> locationIds = Collections.singletonList(identifier);
            return filterMode != null && filterMode.equals(Constants.LOCATION_FILTER_MODE_LINEAGE)
                    ? getPaginatedLocations(request, locationIds)
                    : getPaginatedLocationsBackwardCompatibility(request, locationIds);
        } else {
            LocationHierarchy locationHierarchy =
                    getLocationHierarchy(
                            identifier,
                            preFetchAdminLevels,
                            postFetchAdminLevels,
                            filterInventory,
                            lastUpdated);
            return Utils.createBundle(Collections.singletonList(locationHierarchy));
        }
    }

    public Bundle handleNonIdentifierRequest(
            HttpServletRequest request,
            PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper,
            DecodedJWT verifiedJwt) {
        String mode = request.getParameter(Constants.MODE);
        String syncLocationsParam = request.getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        String filterMode = request.getParameter(Constants.LOCATION_FILTER_MODE);
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);
        List<String> selectedSyncLocations = extractSyncLocations(syncLocationsParam);
        String practitionerId = verifiedJwt.getSubject();
        List<String> userRoles = JwtUtils.getUserRolesFromJWT(verifiedJwt);
        String applicationId = JwtUtils.getApplicationIdFromJWT(verifiedJwt);
        String syncStrategy = getSyncStrategyByAppId(applicationId);
        String lastUpdated = "";

        if (Constants.LIST.equals(mode)) {
            if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)
                    && userRoles.contains(Constants.ROLE_ALL_LOCATIONS)
                    && !selectedSyncLocations.isEmpty()) {
                return filterMode != null
                                && filterMode.equals(Constants.LOCATION_FILTER_MODE_LINEAGE)
                        ? getPaginatedLocations(request, selectedSyncLocations)
                        : getPaginatedLocationsBackwardCompatibility(
                                request, selectedSyncLocations);

            } else {
                List<String> locationIds =
                        practitionerDetailsEndpointHelper.getPractitionerLocationIdsByByKeycloakId(
                                practitionerId);
                return filterMode != null
                                && filterMode.equals(Constants.LOCATION_FILTER_MODE_LINEAGE)
                        ? getPaginatedLocations(request, locationIds)
                        : getPaginatedLocationsBackwardCompatibility(
                                request, selectedSyncLocations);
            }

        } else {
            if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)
                    && userRoles.contains(Constants.ROLE_ALL_LOCATIONS)
                    && !selectedSyncLocations.isEmpty()) {
                List<LocationHierarchy> locationHierarchies =
                        getLocationHierarchies(
                                selectedSyncLocations,
                                preFetchAdminLevels,
                                postFetchAdminLevels,
                                filterInventory,
                                lastUpdated);
                List<Resource> resourceList =
                        locationHierarchies != null
                                ? locationHierarchies.stream()
                                        .map(locationHierarchy -> (Resource) locationHierarchy)
                                        .collect(Collectors.toList())
                                : Collections.emptyList();
                return Utils.createBundle(resourceList);
            } else {
                List<String> locationIds =
                        practitionerDetailsEndpointHelper.getPractitionerLocationIdsByByKeycloakId(
                                practitionerId);
                List<LocationHierarchy> locationHierarchies =
                        getLocationHierarchies(
                                locationIds,
                                preFetchAdminLevels,
                                postFetchAdminLevels,
                                filterInventory,
                                lastUpdated);
                List<Resource> resourceList =
                        locationHierarchies != null
                                ? locationHierarchies.stream()
                                        .map(locationHierarchy -> (Resource) locationHierarchy)
                                        .collect(Collectors.toList())
                                : Collections.emptyList();
                return Utils.createBundle(resourceList);
            }
        }
    }

    public String getSyncStrategyByAppId(String applicationId) {
        String syncStrategy;
        if (CacheHelper.INSTANCE.skipCache()) {
            syncStrategy = getSyncStrategy(applicationId);
        } else {
            syncStrategy =
                    CacheHelper.INSTANCE.stringCache.get(
                            applicationId, key -> getSyncStrategy(applicationId));
        }
        return syncStrategy;
    }

    private String getSyncStrategy(String applicationId) {
        FhirContext fhirContext = FhirContext.forR4();
        IGenericClient client = Utils.createFhirClientForR4(fhirContext);

        Bundle compositionBundle =
                client.search()
                        .forResource(Composition.class)
                        .where(Composition.IDENTIFIER.exactly().identifier(applicationId))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        Bundle.BundleEntryComponent compositionEntry = compositionBundle.getEntryFirstRep();
        Composition composition;
        if (compositionEntry != null) {
            composition = (Composition) compositionEntry.getResource();
        } else {
            composition = null;
        }
        String binaryResourceReference = Utils.getBinaryResourceReference(composition);
        Binary binary =
                Utils.readApplicationConfigBinaryResource(binaryResourceReference, fhirContext);
        return Utils.findSyncStrategy(binary);
    }

    public List<String> extractSyncLocations(String syncLocationsParam) {
        List<String> selectedSyncLocations = new ArrayList<>();
        if (syncLocationsParam != null && !syncLocationsParam.isEmpty()) {
            Collections.addAll(selectedSyncLocations, syncLocationsParam.split(","));
        }
        return selectedSyncLocations;
    }

    public Bundle getPaginatedLocations(HttpServletRequest request, List<String> locationIds) {
        String pageSize = request.getParameter(Constants.PAGINATION_PAGE_SIZE);
        String pageNumber = request.getParameter(Constants.PAGINATION_PAGE_NUMBER);
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        String lastUpdated = request.getParameter(Constants.LAST_UPDATED);
        String summary = request.getParameter(Constants.SUMMARY);
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
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

        List<Location> resourceLocations =
                locationIds.stream()
                        .map(locationId -> fetchAllDescendants(locationId, preFetchAdminLevels))
                        .flatMap(descendant -> descendant.getEntry().stream())
                        .map(bundleEntryComponent -> (Location) bundleEntryComponent.getResource())
                        .collect(Collectors.toList());

        // Get the parents
        Bundle parentLocation = getLocationById(locationIds);
        if (parentLocation != null) {
            List<Bundle.BundleEntryComponent> locationBundleEntryComponents =
                    parentLocation.getEntry();
            for (Bundle.BundleEntryComponent locationBundleEntryComponent :
                    locationBundleEntryComponents) {
                resourceLocations.add((Location) locationBundleEntryComponent.getResource());
            }
        }

        // Apply the post filter
        resourceLocations =
                postFetchFilters(
                        resourceLocations, postFetchAdminLevels, filterInventory, lastUpdated);

        int totalEntries = resourceLocations.size();

        int end = Math.min(start + count, resourceLocations.size());
        List<Location> paginatedResourceLocations = resourceLocations.subList(start, end);
        Bundle resultBundle;
        if (Constants.COUNT.equals(summary)) {
            resultBundle =
                    Utils.createEmptyBundle(
                            request.getRequestURL() + "?" + request.getQueryString());
            resultBundle.setTotal(totalEntries);
            return resultBundle;
        }

        if (resourceLocations.isEmpty()) {
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

    @Deprecated(since = "2.3.0", forRemoval = true)
    public Bundle getPaginatedLocationsBackwardCompatibility(
            HttpServletRequest request, List<String> locationIds) {
        String pageSize = request.getParameter(Constants.PAGINATION_PAGE_SIZE);
        String pageNumber = request.getParameter(Constants.PAGINATION_PAGE_NUMBER);
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        String lastUpdated = request.getParameter(Constants.LAST_UPDATED);
        String summary = request.getParameter(Constants.SUMMARY);
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
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

        List<Resource> resourceLocations =
                locationIds.parallelStream()
                        .flatMap(
                                identifier ->
                                        getLocationHierarchyLocations(
                                                identifier,
                                                preFetchAdminLevels,
                                                postFetchAdminLevels,
                                                filterInventory,
                                                lastUpdated)
                                                .stream())
                        .collect(Collectors.toList());
        int totalEntries = resourceLocations.size();

        int end = Math.min(start + count, resourceLocations.size());
        List<Resource> paginatedResourceLocations = resourceLocations.subList(start, end);
        Bundle resultBundle;
        if (Constants.COUNT.equals(summary)) {
            resultBundle =
                    Utils.createEmptyBundle(
                            request.getRequestURL() + "?" + request.getQueryString());
            resultBundle.setTotal(totalEntries);
            return resultBundle;
        }

        if (resourceLocations.isEmpty()) {
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

    public Bundle fetchAllDescendants(String locationId, List<String> preFetchAdminLevels) {
        StringBuilder queryStringFilter = new StringBuilder("Location?");
        if (StringUtils.isNotBlank(locationId)) {
            queryStringFilter
                    .append("&_tag=")
                    .append(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY)
                    .append("%7C")
                    .append(locationId)
                    .append(',');
        }

        if (preFetchAdminLevels != null && !preFetchAdminLevels.isEmpty()) {
            queryStringFilter.append("&type=");
            for (String adminLevel : preFetchAdminLevels) {
                queryStringFilter
                        .append(Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL)
                        .append("%7C")
                        .append(adminLevel)
                        .append(',');
            }
        }

        return (Bundle) getFhirClientForR4().search().byUrl(queryStringFilter.toString()).execute();
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

    public List<Location> postFetchFilters(
            List<Location> locations,
            List<String> postFetchAdminLevels,
            boolean filterByInventory,
            String lastUpdated) {
        return locations.stream()
                .filter(
                        location ->
                                postFetchAdminLevels == null
                                        || postFetchAdminLevels.isEmpty()
                                        || adminLevelFilter(location, postFetchAdminLevels))
                .filter(
                        location ->
                                lastUpdated == null
                                        || lastUpdated.isBlank()
                                        || lastUpdatedFilter(location, lastUpdated))
                .filter(location -> !filterByInventory || inventoryFilter(location))
                .collect(Collectors.toList());
    }

    public boolean adminLevelFilter(Location location, List<String> postFetchAdminLevels) {
        return location.getType().stream()
                .flatMap(codeableConcept -> codeableConcept.getCoding().stream())
                .anyMatch(
                        coding ->
                                Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL.equals(coding.getSystem())
                                        && postFetchAdminLevels.contains(coding.getCode()));
    }

    public boolean lastUpdatedFilter(Location location, String lastUpdated) {
        Date metaLocationLastUpdated = location.getMeta().getLastUpdated();
        OffsetDateTime locationLastUpdated =
                metaLocationLastUpdated.toInstant().atOffset(ZoneOffset.UTC);
        return locationLastUpdated.isAfter(OffsetDateTime.parse(lastUpdated))
                || locationLastUpdated.isEqual(OffsetDateTime.parse(lastUpdated));
    }

    public boolean inventoryFilter(Location location) {
        String locationId = location.getIdElement().getIdPart();
        String locationReference =
                Constants.SyncStrategy.LOCATION + Constants.FORWARD_SLASH + locationId;

        Bundle listBundle =
                getFhirClientForR4()
                        .search()
                        .forResource(ListResource.class)
                        .where(new ReferenceClientParam(Constants.SUBJECT).hasId(locationReference))
                        .usingStyle(SearchStyleEnum.POST)
                        .returnBundle(Bundle.class)
                        .execute();

        return listBundle != null && !listBundle.getEntry().isEmpty();
    }
}
