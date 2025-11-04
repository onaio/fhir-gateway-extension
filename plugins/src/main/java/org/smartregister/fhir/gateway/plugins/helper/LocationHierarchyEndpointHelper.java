package org.smartregister.fhir.gateway.plugins.helper;

import static org.smartregister.utils.Constants.LOCATION_RESOURCE;
import static org.smartregister.utils.Constants.LOCATION_RESOURCE_NOT_FOUND;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smartregister.fhir.gateway.plugins.Constants;
import org.smartregister.fhir.gateway.plugins.SyncAccessDecision;
import org.smartregister.fhir.gateway.plugins.utils.JwtUtils;
import org.smartregister.fhir.gateway.plugins.utils.Utils;
import org.smartregister.model.location.LocationHierarchy;
import org.smartregister.model.location.LocationHierarchyTree;

import com.auth0.jwt.interfaces.DecodedJWT;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LocationHierarchyEndpointHelper extends BaseFhirEndpointHelper {

    private static final Logger logger =
            LoggerFactory.getLogger(LocationHierarchyEndpointHelper.class);

    private final StreamingResponseHelper streamingHelper;
    private final PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public LocationHierarchyEndpointHelper(IGenericClient fhirClient) {
        super(fhirClient);
        this.streamingHelper =
                new StreamingResponseHelper(
                        FhirContext.forR4Cached().newJsonParser().setPrettyPrint(true));
        this.practitionerDetailsEndpointHelper = new PractitionerDetailsEndpointHelper(fhirClient);
    }

    public LocationHierarchy getLocationHierarchy(
            String locationId,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated) {
        // Create cache key that includes all parameters
        String cacheKey =
                String.format(
                        "%s_%s_%s_%s_%s",
                        locationId,
                        String.join(
                                ",",
                                preFetchAdminLevels != null
                                        ? preFetchAdminLevels
                                        : java.util.Collections.emptyList()),
                        String.join(
                                ",",
                                postFetchAdminLevels != null
                                        ? postFetchAdminLevels
                                        : java.util.Collections.emptyList()),
                        filterInventory,
                        lastUpdated != null ? lastUpdated : "null");

        if (CacheHelper.INSTANCE.skipCache()) {
            return getLocationHierarchyCore(
                    locationId,
                    preFetchAdminLevels,
                    postFetchAdminLevels,
                    filterInventory,
                    lastUpdated);
        } else {
            // Use resourceCache for LocationHierarchy objects
            return (LocationHierarchy)
                    CacheHelper.INSTANCE.resourceCache.get(
                            cacheKey,
                            key ->
                                    getLocationHierarchyCore(
                                            locationId,
                                            preFetchAdminLevels,
                                            postFetchAdminLevels,
                                            filterInventory,
                                            lastUpdated));
        }
    }

    public List<LocationHierarchy> getLocationHierarchies(
            List<String> locationIds,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated,
            String tagUrl) {

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
        Location location = getLocationByIdWithCache(locationId);

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
        Location parentLocation = getLocationByIdWithCache(locationId);
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

    public @Nullable Location getLocationByIdWithCache(String locationId) {
        if (CacheHelper.INSTANCE.skipCache()) {
            return getLocationById(locationId);
        } else {
            return (Location)
                    CacheHelper.INSTANCE.resourceCache.get(
                            "location_" + locationId, key -> getLocationById(locationId));
        }
    }

    public Bundle handleIdentifierRequest(HttpServletRequest request, String identifier) {
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        String mode = request.getParameter(Constants.MODE);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        boolean filterModeLineage =
                request.getParameterMap().containsKey(Constants.FILTER_MODE_LINEAGE)
                        && (StringUtils.isBlank(request.getParameter(Constants.FILTER_MODE_LINEAGE))
                                || Boolean.parseBoolean(
                                        request.getParameter(Constants.FILTER_MODE_LINEAGE)));
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);
        if (Constants.LIST.equals(mode)) {
            List<String> locationIds = Collections.singletonList(identifier);
            // Use default location hierarchy tag for identifier-based requests
            return filterModeLineage
                    ? getPaginatedLocations(request, locationIds, null)
                    : getPaginatedLocationsBackwardCompatibility(request, locationIds);
        } else {
            LocationHierarchy locationHierarchy =
                    getLocationHierarchy(
                            identifier,
                            preFetchAdminLevels,
                            postFetchAdminLevels,
                            filterInventory,
                            null);
            return Utils.createBundle(Collections.singletonList(locationHierarchy));
        }
    }

    public Bundle handleNonIdentifierRequest(HttpServletRequest request, DecodedJWT verifiedJwt) {
        String mode = request.getParameter(Constants.MODE);
        String syncLocationsParam = request.getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        boolean filterModeLineage =
                request.getParameterMap().containsKey(Constants.FILTER_MODE_LINEAGE)
                        && (StringUtils.isBlank(request.getParameter(Constants.FILTER_MODE_LINEAGE))
                                || Boolean.parseBoolean(
                                        request.getParameter(Constants.FILTER_MODE_LINEAGE)));
        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);
        List<String> selectedSyncLocations = extractSyncLocations(syncLocationsParam);
        String keyCloakPractitionerId = verifiedJwt.getSubject();
        List<String> userRoles = JwtUtils.getUserRolesFromJWT(verifiedJwt);
        String applicationId = JwtUtils.getApplicationIdFromJWT(verifiedJwt);
        String syncStrategy = getSyncStrategyByAppId(applicationId);

        if (Constants.LIST.equals(mode)) {
            if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)
                    && userRoles.contains(Constants.ROLE_ALL_LOCATIONS)
                    && !selectedSyncLocations.isEmpty()) {
                // Use Related Entity Location tag for _syncLocations with
                // RELATED_ENTITY_LOCATION
                // strategy
                String tagUrl = getTagUrlForSyncStrategy(syncStrategy);
                return filterModeLineage
                        ? getPaginatedLocations(request, selectedSyncLocations, tagUrl)
                        : getPaginatedLocationsBackwardCompatibility(
                                request, selectedSyncLocations);
            } else {
                List<String> locationIds =
                        getPractitionerLocationIdsByKeycloakId(keyCloakPractitionerId);
                return filterModeLineage
                        ? getPaginatedLocations(request, locationIds, null)
                        : getPaginatedLocationsBackwardCompatibility(
                                request, selectedSyncLocations);
            }

        } else {
            if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)
                    && userRoles.contains(Constants.ROLE_ALL_LOCATIONS)
                    && !selectedSyncLocations.isEmpty()) {
                // Use Related Entity Location tag for _syncLocations with
                // RELATED_ENTITY_LOCATION
                // strategy
                String tagUrl = getTagUrlForSyncStrategy(syncStrategy);
                List<LocationHierarchy> locationHierarchies =
                        getLocationHierarchies(
                                selectedSyncLocations,
                                preFetchAdminLevels,
                                postFetchAdminLevels,
                                filterInventory,
                                null,
                                tagUrl);
                List<Resource> resourceList =
                        locationHierarchies != null
                                ? locationHierarchies.stream()
                                        .map(locationHierarchy -> (Resource) locationHierarchy)
                                        .collect(Collectors.toList())
                                : Collections.emptyList();
                return Utils.createBundle(resourceList);
            } else {
                List<String> locationIds =
                        getPractitionerLocationIdsByKeycloakId(keyCloakPractitionerId);
                List<LocationHierarchy> locationHierarchies =
                        getLocationHierarchies(
                                locationIds,
                                preFetchAdminLevels,
                                postFetchAdminLevels,
                                filterInventory,
                                null,
                                null); // Use default tag for practitioner locations
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

    /**
     * Get the tag URL for a given sync strategy. This is used to determine which tag system to use
     * when fetching locations.
     *
     * @param syncStrategy The sync strategy (e.g., RELATED_ENTITY_LOCATION)
     * @return The tag URL for the sync strategy, or SYSTEM_LOCATION_HIERARCHY as default
     */
    private String getTagUrlForSyncStrategy(String syncStrategy) {
        if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)) {
            String envVar = System.getenv(Constants.RELATED_ENTITY_TAG_URL_ENV);
            return envVar != null && !envVar.isEmpty()
                    ? envVar
                    : Constants.DEFAULT_RELATED_ENTITY_TAG_URL;
        }
        // Default to location hierarchy tag for other sync strategies
        return Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY;
    }

    /**
     * Check if a tag URL is for RELATED_ENTITY_LOCATION (either default or from environment
     * variable).
     *
     * @param tagUrl The tag URL to check
     * @return true if the tag URL is for RELATED_ENTITY_LOCATION, false otherwise
     */
    private boolean isRelatedEntityLocationTagUrl(String tagUrl) {
        if (tagUrl == null) {
            return false;
        }
        // Check if it's the default RELATED_ENTITY_LOCATION tag URL
        if (Constants.DEFAULT_RELATED_ENTITY_TAG_URL.equals(tagUrl)) {
            return true;
        }
        // Check if it's the RELATED_ENTITY_LOCATION tag URL from environment variable
        String envVar = System.getenv(Constants.RELATED_ENTITY_TAG_URL_ENV);
        if (envVar != null && !envVar.isEmpty() && envVar.equals(tagUrl)) {
            return true;
        }
        return false;
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
        String binaryResourceReference = Utils.getBinaryResourceReference(composition, logger);
        Binary binary =
                Utils.readApplicationConfigBinaryResource(binaryResourceReference, fhirContext);
        return Utils.findSyncStrategy(binary);
    }

    public Bundle getPaginatedLocations(
            HttpServletRequest request, List<String> locationIds, String tagUrl) {
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

        // Fetch all descendants for all location IDs in a single query
        // Use provided tag URL or default to location hierarchy tag
        Bundle allDescendantsBundle = fetchAllDescendants(locationIds, preFetchAdminLevels, tagUrl);
        List<Location> resourceLocations =
                allDescendantsBundle.getEntry().stream()
                        .map(bundleEntryComponent -> (Location) bundleEntryComponent.getResource())
                        .collect(Collectors.toList());

        // Get the parents
        Bundle parentLocation = getLocationsById(locationIds);
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

    /**
     * Stream paginated locations for large datasets to improve memory usage and response time. This
     * method uses streaming when the dataset is large enough to benefit from it.
     */
    public void streamPaginatedLocations(
            HttpServletRequest request,
            HttpServletResponse response,
            List<String> locationIds,
            DecodedJWT verifiedJwt)
            throws IOException {

        String pageSize = request.getParameter(Constants.PAGINATION_PAGE_SIZE);
        String pageNumber = request.getParameter(Constants.PAGINATION_PAGE_NUMBER);
        String administrativeLevelMin = request.getParameter(Constants.MIN_ADMIN_LEVEL);
        String administrativeLevelMax = request.getParameter(Constants.MAX_ADMIN_LEVEL);
        Boolean filterInventory = Boolean.valueOf(request.getParameter(Constants.FILTER_INVENTORY));
        String lastUpdated = request.getParameter(Constants.LAST_UPDATED);

        List<String> preFetchAdminLevels =
                generateAdminLevels(
                        String.valueOf(Constants.DEFAULT_MIN_ADMIN_LEVEL), administrativeLevelMax);
        List<String> postFetchAdminLevels =
                generateAdminLevels(administrativeLevelMin, administrativeLevelMax);

        int count =
                pageSize != null
                        ? Integer.parseInt(pageSize)
                        : Constants.PAGINATION_DEFAULT_PAGE_SIZE;
        int page =
                pageNumber != null
                        ? Integer.parseInt(pageNumber)
                        : Constants.PAGINATION_DEFAULT_PAGE_NUMBER;

        // Determine tag URL based on sync strategy when _syncLocations is used
        String tagUrl = null;
        String syncLocationsParam = request.getParameter(Constants.SYNC_LOCATIONS_SEARCH_PARAM);
        if (syncLocationsParam != null && !syncLocationsParam.isEmpty() && verifiedJwt != null) {
            // Check if sync strategy is RELATED_ENTITY_LOCATION
            String applicationId = JwtUtils.getApplicationIdFromJWT(verifiedJwt);
            if (applicationId != null) {
                String syncStrategy = getSyncStrategyByAppId(applicationId);
                tagUrl = getTagUrlForSyncStrategy(syncStrategy);
            }
        }

        // Fetch all descendants for all location IDs in a single query
        Bundle allDescendantsBundle = fetchAllDescendants(locationIds, preFetchAdminLevels, tagUrl);
        List<Location> resourceLocations =
                allDescendantsBundle.getEntry().stream()
                        .map(bundleEntryComponent -> (Location) bundleEntryComponent.getResource())
                        .collect(Collectors.toList());

        // Get the parents
        Bundle parentLocation = getLocationsById(locationIds);
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

        // Check if we should use streaming based on dataset size
        int streamingThreshold = 1000; // Use streaming for datasets larger than 1000 locations
        if (StreamingResponseHelper.shouldUseStreaming(totalEntries, streamingThreshold)) {
            logger.info(
                    "Using memory-efficient streaming for large dataset with {} locations",
                    totalEntries);
            // Use true streaming with chunked data fetching
            streamPaginatedLocationsMemoryEfficient(
                    request,
                    response,
                    locationIds,
                    preFetchAdminLevels,
                    postFetchAdminLevels,
                    filterInventory,
                    lastUpdated,
                    page,
                    count,
                    tagUrl);
        } else {
            // For smaller datasets, use regular pagination
            logger.info("Using regular pagination for dataset with {} locations", totalEntries);
            Bundle resultBundle = getPaginatedLocations(request, locationIds, tagUrl);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(
                        FhirContext.forR4Cached()
                                .newJsonParser()
                                .setPrettyPrint(true)
                                .encodeResourceToString(resultBundle));
            }
        }
    }

    /** Memory-efficient streaming that fetches and processes data in chunks. */
    private void streamPaginatedLocationsMemoryEfficient(
            HttpServletRequest request,
            HttpServletResponse response,
            List<String> locationIds,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated,
            int page,
            int pageSize,
            String tagUrl)
            throws IOException {

        // First, get total count without loading all data
        int totalCount =
                getLocationCount(
                        locationIds,
                        preFetchAdminLevels,
                        postFetchAdminLevels,
                        filterInventory,
                        lastUpdated,
                        tagUrl);

        logger.info("Total locations to stream: {}", totalCount);

        // Create a data provider function that fetches data using offset and limit
        // The provider accepts (offset, limit) where:
        // - offset: the starting position in the result set (0-based)
        // - limit: the maximum number of items to return
        String finalTagUrl = tagUrl; // Capture for lambda
        java.util.function.BiFunction<Integer, Integer, List<Location>> dataProvider =
                (offset, limit) -> {
                    try {
                        logger.debug(
                                "Fetching location chunk with offset={}, limit={}", offset, limit);
                        return fetchLocationChunk(
                                locationIds,
                                preFetchAdminLevels,
                                postFetchAdminLevels,
                                filterInventory,
                                lastUpdated,
                                offset,
                                limit,
                                finalTagUrl);
                    } catch (Exception e) {
                        logger.error(
                                "Error fetching location chunk with offset={}, limit={}",
                                offset,
                                limit,
                                e);
                        return new ArrayList<>();
                    }
                };

        // Use the memory-efficient streaming method
        streamingHelper.streamLocationBundleWithChunking(
                request, response, dataProvider, totalCount, page, pageSize);
    }

    /** Get total count of locations without loading all data into memory. */
    private int getLocationCount(
            List<String> locationIds,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated,
            String tagUrl) {
        try {
            // Use a count query to get total without loading data
            StringBuilder queryStringFilter = new StringBuilder("Location?_count=0");

            // Use provided tag URL or default to location hierarchy tag
            String tagSystem =
                    tagUrl != null ? tagUrl : Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY;

            // When using RELATED_ENTITY_LOCATION tag, also include location-lineage tags
            // to match locations that have either tag system
            String locationIdsParam = buildCommaSeparatedValues(locationIds, tagSystem);
            if (Constants.DEFAULT_RELATED_ENTITY_TAG_URL.equals(tagSystem)) {
                // Also query for location-lineage tags to match locations with either tag
                // system
                String locationLineageParam =
                        buildCommaSeparatedValues(
                                locationIds, Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY);
                if (!locationLineageParam.isEmpty()) {
                    // Combine both tag systems with comma (OR logic in FHIR)
                    if (!locationIdsParam.isEmpty()) {
                        locationIdsParam = locationIdsParam + "," + locationLineageParam;
                    } else {
                        locationIdsParam = locationLineageParam;
                    }
                }
            }
            if (!locationIdsParam.isEmpty()) {
                queryStringFilter.append("&_tag=").append(locationIdsParam);
            }

            // Add admin levels parameter
            String adminLevelsParam =
                    buildCommaSeparatedValues(
                            preFetchAdminLevels, Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL);
            if (!adminLevelsParam.isEmpty()) {
                queryStringFilter.append("&type=").append(adminLevelsParam);
            }

            Bundle countBundle =
                    (Bundle)
                            getFhirClientForR4()
                                    .search()
                                    .byUrl(queryStringFilter.toString())
                                    .execute();
            return countBundle.getTotal();
        } catch (Exception e) {
            logger.error("Error getting location count", e);
            return 0;
        }
    }

    /**
     * Helper method to build comma-separated parameter values without trailing commas. Collects
     * non-blank values, applies prefix encoding, and joins with commas.
     *
     * @param values List of values to join
     * @param prefix Prefix to apply to each value (will be URL-encoded with %7C)
     * @return Comma-separated string of prefixed values, or empty string if no valid values
     */
    private String buildCommaSeparatedValues(List<String> values, String prefix) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        // Collect non-blank values with prefix encoding
        List<String> encodedValues = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                encodedValues.add(prefix + "%7C" + value);
            }
        }

        // Join collected values with commas (no trailing comma)
        return encodedValues.isEmpty() ? "" : String.join(",", encodedValues);
    }

    /**
     * Fetch a chunk of locations for streaming using offset-based pagination.
     *
     * @param locationIds List of location IDs to filter by
     * @param preFetchAdminLevels Admin levels to filter by in the query
     * @param postFetchAdminLevels Admin levels to filter by after fetching
     * @param filterInventory Whether to filter by inventory
     * @param lastUpdated Last updated timestamp filter
     * @param offset The starting position in the result set (0-based)
     * @param limit The maximum number of items to return
     * @param tagUrl The tag URL to use for filtering locations (null for default)
     * @return List of locations matching the criteria
     */
    private List<Location> fetchLocationChunk(
            List<String> locationIds,
            List<String> preFetchAdminLevels,
            List<String> postFetchAdminLevels,
            Boolean filterInventory,
            String lastUpdated,
            int offset,
            int limit,
            String tagUrl) {
        try {
            // Build FHIR query with offset-based pagination
            // _offset: starting position in the result set (0-based)
            // _count: maximum number of results to return
            StringBuilder queryStringFilter = new StringBuilder("Location?");
            queryStringFilter.append("_count=").append(limit);
            queryStringFilter.append("&_offset=").append(offset);

            // Use provided tag URL or default to location hierarchy tag
            String tagSystem =
                    tagUrl != null ? tagUrl : Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY;

            // When using RELATED_ENTITY_LOCATION tag, also include location-lineage tags
            // to match locations that have either tag system
            String locationIdsParam = buildCommaSeparatedValues(locationIds, tagSystem);
            if (Constants.DEFAULT_RELATED_ENTITY_TAG_URL.equals(tagSystem)) {
                // Also query for location-lineage tags to match locations with either tag
                // system
                String locationLineageParam =
                        buildCommaSeparatedValues(
                                locationIds, Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY);
                if (!locationLineageParam.isEmpty()) {
                    // Combine both tag systems with comma (OR logic in FHIR)
                    if (!locationIdsParam.isEmpty()) {
                        locationIdsParam = locationIdsParam + "," + locationLineageParam;
                    } else {
                        locationIdsParam = locationLineageParam;
                    }
                }
            }
            if (!locationIdsParam.isEmpty()) {
                queryStringFilter.append("&_tag=").append(locationIdsParam);
            }

            // Add admin levels parameter
            String adminLevelsParam =
                    buildCommaSeparatedValues(
                            preFetchAdminLevels, Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL);
            if (!adminLevelsParam.isEmpty()) {
                queryStringFilter.append("&type=").append(adminLevelsParam);
            }

            Bundle chunkBundle =
                    (Bundle)
                            getFhirClientForR4()
                                    .search()
                                    .byUrl(queryStringFilter.toString())
                                    .execute();
            List<Location> locations =
                    chunkBundle.getEntry().stream()
                            .map(
                                    bundleEntryComponent ->
                                            (Location) bundleEntryComponent.getResource())
                            .collect(Collectors.toList());

            // Apply post-fetch filters
            return postFetchFilters(locations, postFetchAdminLevels, filterInventory, lastUpdated);
        } catch (Exception e) {
            logger.error("Error fetching location chunk", e);
            return new ArrayList<>();
        }
    }

    @Deprecated(since = "3.0.0", forRemoval = true)
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

    public Bundle fetchAllDescendants(
            List<String> locationIds, List<String> preFetchAdminLevels, String tagUrl) {
        StringBuilder queryStringFilter = new StringBuilder("Location?");

        // Use provided tag URL or default to location hierarchy tag
        String tagSystem = tagUrl != null ? tagUrl : Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY;

        // When using RELATED_ENTITY_LOCATION tag, also include location-lineage tags
        // to match locations that have either tag system
        String locationIdsParam = buildCommaSeparatedValues(locationIds, tagSystem);
        if (isRelatedEntityLocationTagUrl(tagSystem)) {
            // Also query for location-lineage tags to match locations with either tag
            // system
            String locationLineageParam =
                    buildCommaSeparatedValues(
                            locationIds, Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY);
            if (!locationLineageParam.isEmpty()) {
                // Combine both tag systems with comma (OR logic in FHIR)
                if (!locationIdsParam.isEmpty()) {
                    locationIdsParam = locationIdsParam + "," + locationLineageParam;
                } else {
                    locationIdsParam = locationLineageParam;
                }
            }
        }
        if (!locationIdsParam.isEmpty()) {
            queryStringFilter.append("_tag=").append(locationIdsParam);
        }

        // Add admin levels parameter
        String adminLevelsParam =
                buildCommaSeparatedValues(
                        preFetchAdminLevels, Constants.DEFAULT_ADMIN_LEVEL_TYPE_URL);
        if (!adminLevelsParam.isEmpty()) {
            if (queryStringFilter.length() > "Location?".length()) {
                queryStringFilter.append("&");
            }
            queryStringFilter.append("type=").append(adminLevelsParam);
        }

        return (Bundle) getFhirClientForR4().search().byUrl(queryStringFilter.toString()).execute();
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

    @Override
    protected List<String> getPractitionerLocationIdsByKeycloakIdCore(String practitionerId) {
        logger.info("Getting practitioner location IDs for practitioner: {}", practitionerId);
        // Delegate to PractitionerDetailsEndpointHelper to get practitioner locations
        // This uses the core implementation which takes a practitioner ID (FHIR
        // resource ID)
        return practitionerDetailsEndpointHelper.getPractitionerLocationIdsByKeycloakIdCore(
                practitionerId);
    }
}
