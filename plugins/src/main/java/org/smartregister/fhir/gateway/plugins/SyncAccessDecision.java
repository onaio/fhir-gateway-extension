package org.smartregister.fhir.gateway.plugins;

import static ca.uhn.fhir.rest.api.Constants.PARAM_SUMMARY;
import static org.smartregister.fhir.gateway.plugins.EnvUtil.getEnvironmentVar;

import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.util.TextUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.ProxyConstants;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import com.google.gson.Gson;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Getter;

public class SyncAccessDecision implements AccessDecision {
    public static final String SYNC_FILTER_IGNORE_RESOURCES_FILE_ENV =
            "SYNC_FILTER_IGNORE_RESOURCES_FILE";
    public static final String MATCHES_ANY_VALUE = "ANY_VALUE";
    private static final Logger logger = LoggerFactory.getLogger(SyncAccessDecision.class);
    private final String syncStrategy;
    private final boolean accessGranted;
    private final Map<String, List<String>> syncStrategyIdsMap;
    private final List<String> roles;
    private IgnoredResourcesConfig config;
    private final String keycloakUUID;
    private final Gson gson = new Gson();
    private FhirContext fhirR4Context;
    private final IParser fhirR4JsonParser;
    private IGenericClient fhirR4Client;

    private final PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public SyncAccessDecision(
            FhirContext fhirContext,
            String keycloakUUID,
            boolean accessGranted,
            Map<String, List<String>> syncStrategyIdsMap,
            String syncStrategy,
            List<String> roles) {
        this.fhirR4Context = fhirContext;
        this.keycloakUUID = keycloakUUID;
        this.accessGranted = accessGranted;
        this.syncStrategyIdsMap = syncStrategyIdsMap;
        this.syncStrategy = syncStrategy;
        this.config = getSkippedResourcesConfigs();
        this.roles = roles;
        try {
            // Set up HAPI FHIR Generic Client
            this.fhirR4Client =
                    fhirR4Context.newRestfulGenericClient(System.getenv(Constants.PROXY_TO_ENV));
            this.fhirR4Client
                    .getFhirContext()
                    .getRestfulClientFactory()
                    .setConnectionRequestTimeout(300000);
            this.fhirR4Client.getFhirContext().getRestfulClientFactory().setSocketTimeout(300000);

        } catch (NullPointerException e) {
            logger.error(e.getMessage());
        }
        this.fhirR4JsonParser = fhirR4Context.newJsonParser();
        this.practitionerDetailsEndpointHelper =
                new PractitionerDetailsEndpointHelper(fhirR4Client);
    }

    @Override
    public boolean canAccess() {
        return accessGranted;
    }

    @Override
    public RequestMutation getRequestMutation(RequestDetailsReader requestDetailsReader) {

        RequestMutation requestMutation = null;
        String clientRole = getClientRole();

        // Check if it is the Sync URL and Skip app-wide
        if (isSyncUrl(requestDetailsReader)
                && !shouldSkipDataFiltering(requestDetailsReader)
                && clientRole.equals(Constants.ROLE_ANDROID_CLIENT)) {
            // accessible resource requests

            if (syncStrategyIdsMap.isEmpty()
                    || StringUtils.isBlank(syncStrategy)
                    || (syncStrategyIdsMap.containsKey(syncStrategy)
                            && syncStrategyIdsMap.get(syncStrategy).isEmpty())) {

                ForbiddenOperationException forbiddenOperationException =
                        new ForbiddenOperationException(
                                "User un-authorized to "
                                        + requestDetailsReader.getRequestType()
                                        + " /"
                                        + requestDetailsReader.getRequestPath()
                                        + ". User assignment or sync strategy not configured"
                                        + " correctly");
                ExceptionUtil.throwRuntimeExceptionAndLog(
                        logger,
                        forbiddenOperationException.getMessage(),
                        forbiddenOperationException);
            }

            List<String> syncFilterParameterValues;
            Map<String, List<String>> additionalQueryParamsListMap = new HashMap<>();
            if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equals(syncStrategy)) {

                List<String> syncStrategyIdSubList = getRelChunkStrategyIds(0, syncStrategyIdsMap);

                syncFilterParameterValues =
                        addSyncFilters(
                                getSyncTags(
                                        this.syncStrategy,
                                        Map.of(
                                                Constants.SyncStrategy.RELATED_ENTITY_LOCATION,
                                                syncStrategyIdSubList)));

                if (hasSyncLocations(requestDetailsReader)) {

                    String cacheKey = getRelCacheKey(requestDetailsReader);

                    additionalQueryParamsListMap.put(
                            Constants.SYNC_LOCATIONS_HASH_PARAM, List.of(cacheKey));
                }

            } else {
                syncFilterParameterValues =
                        addSyncFilters(getSyncTags(this.syncStrategy, this.syncStrategyIdsMap));
            }

            additionalQueryParamsListMap.put(
                    Constants.TAG_SEARCH_PARAM,
                    List.of(StringUtils.join(syncFilterParameterValues, ",")));

            requestMutation =
                    RequestMutation.builder()
                            .additionalQueryParams(additionalQueryParamsListMap)
                            .discardQueryParams(List.of(Constants.SYNC_LOCATIONS_SEARCH_PARAM))
                            .build();
        }

        return requestMutation;
    }

    private boolean hasSyncLocations(RequestDetailsReader requestDetailsReader) {
        return (requestDetailsReader.getParameters().get(Constants.SYNC_LOCATIONS_SEARCH_PARAM)
                        != null
                && requestDetailsReader
                                .getParameters()
                                .get(Constants.SYNC_LOCATIONS_SEARCH_PARAM)
                                .length
                        > 0);
    }

    private List<String> getRelChunkStrategyIds(
            int startIndex, Map<String, List<String>> syncStrategyIdsMap) {

        int endIndex =
                Math.min(
                        startIndex + SyncAccessDecisionConstants.REL_LOCATION_CHUNK_SIZE,
                        syncStrategyIdsMap
                                .get(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                                .size());

        return this.syncStrategyIdsMap
                .get(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                .subList(startIndex, endIndex);
    }

    private String getClientRole() {
        List<String> matchedRoles = new ArrayList<>();

        for (String role : Constants.CLIENT_ROLES) {
            if (roles.contains(role)) {
                matchedRoles.add(role);
            }
        }
        if (matchedRoles.size() != 1) {
            ForbiddenOperationException forbiddenOperationException =
                    new ForbiddenOperationException(
                            "User must have at least one and at most one of these client roles "
                                    + Arrays.toString(Constants.CLIENT_ROLES));
            ExceptionUtil.throwRuntimeExceptionAndLog(
                    logger, forbiddenOperationException.getMessage(), forbiddenOperationException);
        }
        return matchedRoles.get(0);
    }

    /**
     * Adds filters to the {@link RequestDetailsReader} for the _tag property to allow filtering by
     * specific code-url-values that match specific locations, teams or organisations
     *
     * @param syncTags
     * @return the extra query Parameter values
     */
    private List<String> addSyncFilters(Map<String, String[]> syncTags) {
        List<String> paramValues = new ArrayList<>();

        for (var entry : syncTags.entrySet()) {
            paramValues.add(PractitionerDetailsEndpointHelper.createSearchTagValues(entry));
        }

        return paramValues;
    }

    /** NOTE: Always return a null whenever you want to skip post-processing */
    @Override
    public String postProcess(RequestDetailsReader request, HttpResponse response)
            throws IOException {

        String resultContent = null;
        Resource resultContentBundle;

        Map<String, String[]> parameters = new HashMap<>(request.getParameters());
        String[] listMode = parameters.get(Constants.Header.MODE);
        String gatewayModeQueryParam = listMode != null && listMode.length > 0 ? listMode[0] : null;
        String gatewayMode = request.getHeader(Constants.Header.FHIR_GATEWAY_MODE);
        gatewayMode =
                StringUtils.isNotBlank(gatewayModeQueryParam) ? gatewayModeQueryParam : gatewayMode;

        if (StringUtils.isNotBlank(gatewayMode)) {

            resultContent = new BasicResponseHandler().handleResponse(response);
            IBaseResource responseResource = this.fhirR4JsonParser.parseResource(resultContent);

            if (gatewayMode.equals(SyncAccessDecisionConstants.LIST_ENTRIES)) {
                resultContentBundle = postProcessModeListEntries(responseResource, request);
            } else {
                String exceptionMessage =
                        "The FHIR Gateway Mode header is configured with an un-recognized value"
                                + " of \'"
                                + gatewayMode
                                + '\'';

                resultContentBundle = createOperationOutcome(exceptionMessage);
            }

            resultContent = this.fhirR4JsonParser.encodeResourceToString(resultContentBundle);

        } else if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equals(syncStrategy)) {

            IBaseResource responseResource =
                    processRelatedEntityLocationSyncStrategy(request, response);

            resultContent = this.fhirR4JsonParser.encodeResourceToString(responseResource);

        } else if (includeAttributedPractitioners(request.getRequestPath())) {
            Bundle practitionerDetailsBundle =
                    this.practitionerDetailsEndpointHelper
                            .getSupervisorPractitionerDetailsByKeycloakId(keycloakUUID);
            resultContent = this.fhirR4JsonParser.encodeResourceToString(practitionerDetailsBundle);
        }

        return resultContent;
    }

    // int count = getQueryParamValue("_count", request);

    private String getQueryParamValue(
            String key, RequestDetailsReader requestDetailsReader, String defaultValue) {
        String[] countRaw = requestDetailsReader.getParameters().get(key);
        return countRaw.length > 0 ? countRaw[0] : defaultValue;
    }

    private int getQueryParamIntValue(
            String key, RequestDetailsReader requestDetailsReader, int defaultValue) {
        String[] countRaw = requestDetailsReader.getParameters().getOrDefault(key, new String[] {});
        return countRaw.length > 0 ? Integer.parseInt(countRaw[0]) : defaultValue;
    }

    private IBaseResource processRelatedEntityLocationSyncStrategy(
            RequestDetailsReader request, HttpResponse response) throws IOException {

        String resultContent;

        int totalRecords = 0;

        resultContent = new BasicResponseHandler().handleResponse(response);

        IBaseResource responseResource = this.fhirR4JsonParser.parseResource(resultContent);

        if (responseResource instanceof Bundle) {

            if (syncStrategyIdsMap
                            .getOrDefault(Constants.SyncStrategy.RELATED_ENTITY_LOCATION, List.of())
                            .size()
                    <= SyncAccessDecisionConstants.REL_LOCATION_CHUNK_SIZE) {
                return responseResource;
            }

            // We have more chunks

            // check if we have a live cache , load it and use it else do the processing

            List<Bundle.BundleEntryComponent> cachedRelResults = getCachedRelResults(request);

            if (cachedRelResults.isEmpty()) {

                Utils.fetchAllBundlePagesAndInject(fhirR4Client, ((Bundle) responseResource));
                totalRecords += ((Bundle) responseResource).getTotal();

                String requestPath =
                        request.getRequestPath()
                                + "?"
                                + getRequestParametersString(request.getParameters());

                for (int startIndex = SyncAccessDecisionConstants.REL_LOCATION_CHUNK_SIZE;
                        startIndex
                                < syncStrategyIdsMap
                                        .get(Constants.SyncStrategy.RELATED_ENTITY_LOCATION)
                                        .size();
                        startIndex += SyncAccessDecisionConstants.REL_LOCATION_CHUNK_SIZE) {

                    List<String> syncLocationIds =
                            getRelChunkStrategyIds(startIndex, syncStrategyIdsMap);

                    if (!syncLocationIds.isEmpty()) {
                        StringBuilder requestURL = new StringBuilder(requestPath);

                        for (String entry : syncLocationIds) {
                            requestURL
                                    .append(
                                            "&_tag="
                                                    + Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                                                    + "%7C")
                                    .append(entry)
                                    .append(",");
                        }

                        Bundle paginatedResult =
                                (Bundle)
                                        fhirR4Client
                                                .search()
                                                .byUrl(requestURL.toString())
                                                .usingStyle(SearchStyleEnum.POST)
                                                .execute();
                        Utils.fetchAllBundlePagesAndInject(fhirR4Client, paginatedResult);
                        totalRecords += paginatedResult.getTotal();

                        ((Bundle) responseResource).getEntry().addAll(paginatedResult.getEntry());
                    }
                }

                cacheRelBundleResults(request, (Bundle) responseResource);
                cachedRelResults = ((Bundle) responseResource).getEntry();

                ((Bundle) responseResource).setTotal(totalRecords);

            } else {
                ((Bundle) responseResource).setTotal(cachedRelResults.size());
            }

            if (!request.getParameters().containsKey(PARAM_SUMMARY)) {

                int pageSize =
                        getQueryParamIntValue(
                                Constants.PAGINATION_PAGE_SIZE,
                                request,
                                Constants.PAGINATION_DEFAULT_PAGE_SIZE);

                int page =
                        Math.max(
                                getQueryParamIntValue(
                                        Constants.SYNC_LOCATIONS_PAGE_PARAM, request, 1),
                                1);

                List<Bundle.BundleEntryComponent> bundleEntryComponentList =
                        cachedRelResults.stream()
                                .skip((long) (page - 1) * pageSize)
                                .limit(pageSize)
                                .collect(Collectors.toList());
                ((Bundle) responseResource).setEntry(bundleEntryComponentList);

                // Pagination links
                List<Bundle.BundleLinkComponent> bundlePaginationLinks = new ArrayList<>();

                // Self
                Bundle.BundleLinkComponent selfLinkComponent = new Bundle.BundleLinkComponent();
                selfLinkComponent.setRelation(Bundle.LINK_SELF);
                selfLinkComponent.setUrl(request.getCompleteUrl());
                bundlePaginationLinks.add(selfLinkComponent);

                // Next
                if (cachedRelResults.size() > (page * pageSize)) {
                    Bundle.BundleLinkComponent nextLinkComponent = new Bundle.BundleLinkComponent();
                    nextLinkComponent.setRelation(Bundle.LINK_NEXT);
                    nextLinkComponent.setUrl(getPaginationURL(request, page, true));
                    bundlePaginationLinks.add(nextLinkComponent);
                }

                // Previous
                if (page > 1) {
                    Bundle.BundleLinkComponent previousLinkComponent =
                            new Bundle.BundleLinkComponent();
                    previousLinkComponent.setRelation(Bundle.LINK_PREV);
                    previousLinkComponent.setUrl(getPaginationURL(request, page, false));
                    bundlePaginationLinks.add(previousLinkComponent);
                }
                ((Bundle) responseResource).setLink(bundlePaginationLinks);
            }
        }

        return responseResource;
    }

    private String getPaginationURL(RequestDetailsReader request, int pageNo, boolean isNext) {
        return Utils.replaceQueryParamValue(
                request.getCompleteUrl(),
                Constants.SYNC_LOCATIONS_PAGE_PARAM,
                String.valueOf(isNext ? pageNo + 1 : pageNo - 1));
    }

    private void cacheRelBundleResults(RequestDetailsReader reader, Bundle responseResource) {
        CacheHelper.INSTANCE.resourceListCache.put(
                getRelCacheKey(reader), responseResource.getEntry());
    }

    private List<Bundle.BundleEntryComponent> getCachedRelResults(RequestDetailsReader reader) {
        return CacheHelper.INSTANCE.resourceListCache.get(getRelCacheKey(reader), key -> List.of());
    }

    private String getRelCacheKey(RequestDetailsReader requestDetailsReader) {
        try {
            return requestDetailsReader
                                    .getParameters()
                                    .getOrDefault(
                                            Constants.SYNC_LOCATIONS_HASH_PARAM, new String[] {})
                                    .length
                            > 0
                    ? requestDetailsReader.getParameters()
                            .get(Constants.SYNC_LOCATIONS_HASH_PARAM)[0]
                    : Utils.generateHash(
                            requestDetailsReader.getRequestPath()
                                    + Utils.getSortedInput(
                                            requestDetailsReader.getParameters()
                                                    .get(Constants.SYNC_LOCATIONS_SEARCH_PARAM)[0],
                                            ","));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String getRequestParametersString(Map<String, String[]> parameters) {

        StringBuilder queryString = new StringBuilder();

        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();

            if (!Constants.SYNC_LOCATIONS_SEARCH_PARAM.equals(key)
                    && !Constants.TAG_SEARCH_PARAM.equals(key)) {

                for (String value : values) {
                    queryString.append("&").append(key).append("=").append(value);
                }
            }
        }

        return queryString.toString();
    }

    private boolean includeAttributedPractitioners(String requestPath) {
        return Constants.SyncStrategy.LOCATION.equalsIgnoreCase(syncStrategy)
                && roles.contains(SyncAccessDecisionConstants.ROLE_SUPERVISOR)
                && SyncAccessDecisionConstants.ENDPOINT_PRACTITIONER_DETAILS.equals(requestPath);
    }

    @Nonnull
    private static OperationOutcome createOperationOutcome(String exception) {
        OperationOutcome operationOutcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent operationOutcomeIssueComponent =
                new OperationOutcome.OperationOutcomeIssueComponent();
        operationOutcomeIssueComponent.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        operationOutcomeIssueComponent.setCode(OperationOutcome.IssueType.PROCESSING);
        operationOutcomeIssueComponent.setDiagnostics(exception);
        operationOutcome.setIssue(List.of(operationOutcomeIssueComponent));
        return operationOutcome;
    }

    @Nonnull
    private static Bundle processListEntriesGatewayModeByListResource(
            ListResource responseListResource, int start, int count) {
        Bundle requestBundle = new Bundle();
        requestBundle.setType(Bundle.BundleType.BATCH);

        int end = start + count;

        List<ListResource.ListEntryComponent> entries = responseListResource.getEntry();

        for (int i = start; i < Math.min(end, entries.size()); i++) {
            ListResource.ListEntryComponent listEntryComponent = entries.get(i);
            requestBundle.addEntry(
                    createBundleEntryComponent(
                            Bundle.HTTPVerb.GET,
                            listEntryComponent.getItem().getReference(),
                            null));
        }
        return requestBundle;
    }

    private Bundle processListEntriesGatewayModeByBundle(
            IBaseResource responseResource, int start, int count) {
        Bundle requestBundle = new Bundle();
        requestBundle.setType(Bundle.BundleType.BATCH);

        List<Bundle.BundleEntryComponent> bundleEntryComponentList =
                ((Bundle) responseResource)
                        .getEntry().stream()
                                .filter(it -> it.getResource() instanceof ListResource)
                                .flatMap(
                                        bundleEntryComponent ->
                                                ((ListResource) bundleEntryComponent.getResource())
                                                        .getEntry().stream())
                                .skip(start)
                                .limit(count)
                                .map(
                                        listEntryComponent ->
                                                createBundleEntryComponent(
                                                        Bundle.HTTPVerb.GET,
                                                        listEntryComponent.getItem().getReference(),
                                                        null))
                                .collect(Collectors.toList());

        return requestBundle.setEntry(bundleEntryComponentList);
    }

    @Nonnull
    static Bundle.BundleEntryComponent createBundleEntryComponent(
            Bundle.HTTPVerb method, String requestPath, @Nullable String condition) {

        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setRequest(
                new Bundle.BundleEntryRequestComponent()
                        .setMethod(method)
                        .setUrl(requestPath)
                        .setIfMatch(condition));

        return bundleEntryComponent;
    }

    /**
     * Generates a Bundle result from making a batch search request with the contained entries in
     * the List as parameters
     *
     * @param responseResource FHIR Resource result returned by the HTTPResponse
     * @return String content of the result Bundle
     */
    private Bundle postProcessModeListEntries(
            IBaseResource responseResource, RequestDetailsReader request) {

        Map<String, String[]> parameters = new HashMap<>(request.getParameters());
        String[] pageSize = parameters.get(Constants.PAGINATION_PAGE_SIZE);
        String[] pageNumber = parameters.get(Constants.PAGINATION_PAGE_NUMBER);

        int totalEntries = 0;
        int count =
                pageSize != null && pageSize.length > 0
                        ? Integer.parseInt(pageSize[0])
                        : Constants.PAGINATION_DEFAULT_PAGE_SIZE;
        int page =
                pageNumber != null && pageNumber.length > 0
                        ? Integer.parseInt(pageNumber[0])
                        : Constants.PAGINATION_DEFAULT_PAGE_NUMBER;

        int start = Math.max(0, (page - 1)) * count;
        Bundle requestBundle = null;

        if (responseResource instanceof ListResource
                && ((ListResource) responseResource).hasEntry()) {
            totalEntries = ((ListResource) responseResource).getEntry().size();
            requestBundle =
                    processListEntriesGatewayModeByListResource(
                            (ListResource) responseResource, start, count);

        } else if (responseResource instanceof Bundle) {
            List<Bundle.BundleEntryComponent> entries = ((Bundle) responseResource).getEntry();
            for (Bundle.BundleEntryComponent entry : entries) {
                if (entry.getResource() instanceof ListResource) {
                    totalEntries = ((ListResource) entry.getResource()).getEntry().size();
                    break;
                }
            }

            requestBundle = processListEntriesGatewayModeByBundle(responseResource, start, count);
        }

        Bundle resultBundle = fhirR4Client.transaction().withBundle(requestBundle).execute();

        StringBuilder urlBuilder = new StringBuilder(request.getFhirServerBase());
        urlBuilder.append("/").append(request.getRequestPath());

        return Utils.addPaginationLinks(
                urlBuilder, resultBundle, page, totalEntries, count, parameters);
    }

    private String getSyncTagUrl(String syncStrategy) {
        if (Constants.SyncStrategy.LOCATION.equalsIgnoreCase(syncStrategy)) {
            return getEnvironmentVar(
                    Constants.LOCATION_TAG_URL_ENV, Constants.DEFAULT_LOCATION_TAG_URL);
        } else if (Constants.SyncStrategy.ORGANIZATION.equalsIgnoreCase(syncStrategy)) {
            return getEnvironmentVar(
                    Constants.ORGANISATION_TAG_URL_ENV, Constants.DEFAULT_ORGANISATION_TAG_URL);
        } else if (Constants.SyncStrategy.CARE_TEAM.equalsIgnoreCase(syncStrategy)) {
            return getEnvironmentVar(
                    Constants.CARE_TEAM_TAG_URL_ENV, Constants.DEFAULT_CARE_TEAM_TAG_URL);
        } else if (Constants.SyncStrategy.RELATED_ENTITY_LOCATION.equalsIgnoreCase(syncStrategy)) {
            return getEnvironmentVar(
                    Constants.RELATED_ENTITY_TAG_URL_ENV, Constants.DEFAULT_RELATED_ENTITY_TAG_URL);
        } else {
            return null;
        }
    }

    /* Generates a map of Code.url to multiple Code.Value which contains all the possible filters that
     * will be used in syncing
     *
     * @param syncStrategy
     * @param syncStrategyIds
     * @return Pair of URL to [Code.url, [Code.Value]] map. The URL is complete url
     */
    private Map<String, String[]> getSyncTags(
            String syncStrategy, Map<String, List<String>> syncStrategyIds) {
        StringBuilder sb = new StringBuilder();
        Map<String, String[]> map = new HashMap<>();

        sb.append(Constants.TAG_SEARCH_PARAM);
        sb.append(Constants.Literals.EQUALS);

        String tagUrl = getSyncTagUrl(syncStrategy);
        if (tagUrl != null) {
            addTags(tagUrl, syncStrategyIds.get(syncStrategy), map, sb);
        }

        return map;
    }

    private void addTags(
            String tagUrl,
            List<String> values,
            Map<String, String[]> map,
            StringBuilder urlStringBuilder) {
        int len = values.size();
        if (len > 0) {
            if (urlStringBuilder.length()
                    != (Constants.TAG_SEARCH_PARAM + Constants.Literals.EQUALS).length()) {
                urlStringBuilder.append(Constants.PARAM_VALUES_SEPARATOR);
            }

            map.put(tagUrl, values.toArray(new String[0]));

            int i = 0;
            for (String tagValue : values) {
                urlStringBuilder.append(tagUrl);
                urlStringBuilder.append(Constants.CODE_URL_VALUE_SEPARATOR);
                urlStringBuilder.append(tagValue);

                if (i != len - 1) {
                    urlStringBuilder.append(Constants.PARAM_VALUES_SEPARATOR);
                }
                i++;
            }
        }
    }

    private boolean isSyncUrl(RequestDetailsReader requestDetailsReader) {
        if (requestDetailsReader.getRequestType() == RequestTypeEnum.GET
                && !TextUtils.isEmpty(requestDetailsReader.getResourceName())) {
            String requestPath = requestDetailsReader.getRequestPath();
            return isResourceTypeRequest(
                    requestPath.replace(requestDetailsReader.getFhirServerBase(), ""));
        }

        return false;
    }

    private boolean isResourceTypeRequest(String requestPath) {
        if (!TextUtils.isEmpty(requestPath)) {
            String[] sections = requestPath.split(ProxyConstants.HTTP_URL_SEPARATOR);

            return sections.length == 1
                    || (sections.length == 2 && TextUtils.isEmpty(sections[1]))
                    || (sections.length == 2 && sections[1].startsWith(Constants.UNDERSCORE));
        }

        return false;
    }

    @VisibleForTesting
    protected IgnoredResourcesConfig getIgnoredResourcesConfigFileConfiguration(String configFile) {
        if (configFile != null && !configFile.isEmpty()) {
            try {
                config = gson.fromJson(new FileReader(configFile), IgnoredResourcesConfig.class);
                if (config == null || config.entries == null) {
                    throw new IllegalArgumentException(
                            "A map with a single `entries` array expected!");
                }
                for (IgnoredResourcesConfig entry : config.entries) {
                    if (entry.getPath() == null) {
                        throw new IllegalArgumentException(
                                "Allow-list entries should have a path.");
                    }
                }

            } catch (IOException e) {
                logger.error(
                        "IO error while reading sync-filter skip-list config file {}", configFile);
            }
        }

        return config;
    }

    @VisibleForTesting
    protected IgnoredResourcesConfig getSkippedResourcesConfigs() {
        return getIgnoredResourcesConfigFileConfiguration(
                System.getenv(SYNC_FILTER_IGNORE_RESOURCES_FILE_ENV));
    }

    /**
     * This method checks the request to ensure the path, request type and parameters match values
     * in the hapi_sync_filter_ignored_queries configuration
     */
    private boolean shouldSkipDataFiltering(RequestDetailsReader requestDetailsReader) {
        if (config == null) return false;

        for (IgnoredResourcesConfig entry : config.entries) {

            if (!entry.getPath().equals(requestDetailsReader.getRequestPath())) {
                continue;
            }

            if (entry.getMethodType() != null
                    && !entry.getMethodType()
                            .equals(requestDetailsReader.getRequestType().name())) {
                continue;
            }

            for (Map.Entry<String, Object> expectedParam : entry.getQueryParams().entrySet()) {
                String[] actualQueryValue =
                        requestDetailsReader.getParameters().get(expectedParam.getKey());

                if (actualQueryValue == null) {
                    return true;
                }

                if (MATCHES_ANY_VALUE.equals(expectedParam.getValue())) {
                    return true;
                } else {
                    if (actualQueryValue.length != 1) {
                        // We currently do not support multivalued query params in skip-lists.
                        return false;
                    }

                    if (expectedParam.getValue() instanceof List) {
                        return CollectionUtils.isEqualCollection(
                                (List) expectedParam.getValue(),
                                Arrays.asList(actualQueryValue[0].split(",")));

                    } else if (actualQueryValue[0].equals(expectedParam.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @VisibleForTesting
    protected void setSkippedResourcesConfig(IgnoredResourcesConfig config) {
        this.config = config;
    }

    @VisibleForTesting
    protected void setFhirR4Context(FhirContext fhirR4Context) {
        this.fhirR4Context = fhirR4Context;
    }

    @VisibleForTesting
    public void setFhirR4Client(IGenericClient fhirR4Client) {
        this.fhirR4Client = fhirR4Client;
    }

    class IgnoredResourcesConfig {
        @Getter List<IgnoredResourcesConfig> entries;
        @Getter private String path;
        @Getter private String methodType;
        @Getter private Map<String, Object> queryParams;

        @Override
        public String toString() {
            return "SkippedFilesConfig{"
                    + methodType
                    + " path="
                    + path
                    + " fhirResources="
                    + Arrays.toString(queryParams.entrySet().toArray())
                    + '}';
        }
    }

    public static final class SyncAccessDecisionConstants {
        public static final String LIST_ENTRIES = "list-entries";
        public static final String ROLE_SUPERVISOR = "SUPERVISOR";
        public static final String ENDPOINT_PRACTITIONER_DETAILS = "PractitionerDetail";
        public static final int REL_LOCATION_CHUNK_SIZE =
                20; // Magic Number Alert - Do not change value for maximum stability
    }
}
