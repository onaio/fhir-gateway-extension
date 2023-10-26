package org.smartregister.fhir.gateway.plugins;

import static org.smartregister.fhir.gateway.plugins.Constants.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.fhir.gateway.ExceptionUtil;
import com.google.fhir.gateway.interfaces.AccessDecision;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;
import com.google.gson.Gson;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import lombok.Getter;

public class SyncAccessDecision implements AccessDecision {
    public static final String SYNC_FILTER_IGNORE_RESOURCES_FILE_ENV =
            "SYNC_FILTER_IGNORE_RESOURCES_FILE";
    public static final String MATCHES_ANY_VALUE = "ANY_VALUE";
    private static final Logger logger = LoggerFactory.getLogger(SyncAccessDecision.class);
    private final String syncStrategy;
    private final String applicationId;
    private final boolean accessGranted;
    private final Map<String, List<String>> syncStrategyIds;
    private final List<String> roles;
    private IgnoredResourcesConfig config;
    private String keycloakUUID;
    private Gson gson = new Gson();
    private FhirContext fhirR4Context;
    private IParser fhirR4JsonParser;
    private IGenericClient fhirR4Client;

    private PractitionerDetailsEndpointHelper practitionerDetailsEndpointHelper;

    public SyncAccessDecision(
            FhirContext fhirContext,
            String keycloakUUID,
            String applicationId,
            boolean accessGranted,
            Map<String, List<String>> syncStrategyIds,
            String syncStrategy,
            List<String> roles) {
        this.fhirR4Context = fhirContext;
        this.keycloakUUID = keycloakUUID;
        this.applicationId = applicationId;
        this.accessGranted = accessGranted;
        this.syncStrategyIds = syncStrategyIds;
        this.syncStrategy = syncStrategy;
        this.config = getSkippedResourcesConfigs();
        this.roles = roles;
        try {
            setFhirR4Client(
                    fhirR4Context.newRestfulGenericClient(
                            System.getenv(PermissionAccessChecker.Factory.PROXY_TO_ENV)));
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
        if (isSyncUrl(requestDetailsReader)) {
            if (syncStrategyIds.isEmpty()) {

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

            // Skip app-wide global resource requests
            if (!shouldSkipDataFiltering(requestDetailsReader)) {
                List<String> syncFilterParameterValues =
                        addSyncFilters(getSyncTags(this.syncStrategy, this.syncStrategyIds));
                requestMutation =
                        RequestMutation.builder()
                                .queryParams(
                                        Map.of(
                                                TAG_SEARCH_PARAM,
                                                Arrays.asList(
                                                        StringUtils.join(
                                                                syncFilterParameterValues, ","))))
                                .build();
            }
        }

        return requestMutation;
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
        String gatewayMode = request.getHeader(SyncAccessDecision.Constants.FHIR_GATEWAY_MODE);

        if (StringUtils.isNotBlank(gatewayMode)) {

            resultContent = new BasicResponseHandler().handleResponse(response);
            IBaseResource responseResource = this.fhirR4JsonParser.parseResource(resultContent);

            switch (gatewayMode) {
                case SyncAccessDecision.Constants.LIST_ENTRIES:
                    resultContentBundle = postProcessModeListEntries(responseResource);
                    break;

                default:
                    String exceptionMessage =
                            "The FHIR Gateway Mode header is configured with an un-recognized value"
                                    + " of \'"
                                    + gatewayMode
                                    + '\'';
                    OperationOutcome operationOutcome = createOperationOutcome(exceptionMessage);

                    resultContentBundle = operationOutcome;
            }

            if (resultContentBundle != null)
                resultContent = this.fhirR4JsonParser.encodeResourceToString(resultContentBundle);
        }

        if (includeAttributedPractitioners(request.getRequestPath())) {
            Bundle practitionerDetailsBundle =
                    this.practitionerDetailsEndpointHelper
                            .getSupervisorPractitionerDetailsByKeycloakId(keycloakUUID);
            resultContent = this.fhirR4JsonParser.encodeResourceToString(practitionerDetailsBundle);
        }

        return resultContent;
    }

    private boolean includeAttributedPractitioners(String requestPath) {
        return SyncAccessDecision.Constants.SYNC_STRATEGY_LOCATION.equalsIgnoreCase(syncStrategy)
                && roles.contains(SyncAccessDecision.Constants.ROLE_SUPERVISOR)
                && SyncAccessDecision.Constants.ENDPOINT_PRACTITIONER_DETAILS.equals(requestPath);
    }

    @NotNull
    private static OperationOutcome createOperationOutcome(String exception) {
        OperationOutcome operationOutcome = new OperationOutcome();
        OperationOutcome.OperationOutcomeIssueComponent operationOutcomeIssueComponent =
                new OperationOutcome.OperationOutcomeIssueComponent();
        operationOutcomeIssueComponent.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        operationOutcomeIssueComponent.setCode(OperationOutcome.IssueType.PROCESSING);
        operationOutcomeIssueComponent.setDiagnostics(exception);
        operationOutcome.setIssue(Arrays.asList(operationOutcomeIssueComponent));
        return operationOutcome;
    }

    @NotNull
    private static Bundle processListEntriesGatewayModeByListResource(
            ListResource responseListResource) {
        Bundle requestBundle = new Bundle();
        requestBundle.setType(Bundle.BundleType.BATCH);

        for (ListResource.ListEntryComponent listEntryComponent : responseListResource.getEntry()) {
            requestBundle.addEntry(
                    createBundleEntryComponent(
                            Bundle.HTTPVerb.GET,
                            listEntryComponent.getItem().getReference(),
                            null));
        }
        return requestBundle;
    }

    private Bundle processListEntriesGatewayModeByBundle(IBaseResource responseResource) {
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
                                .map(
                                        listEntryComponent ->
                                                createBundleEntryComponent(
                                                        Bundle.HTTPVerb.GET,
                                                        listEntryComponent.getItem().getReference(),
                                                        null))
                                .collect(Collectors.toList());

        return requestBundle.setEntry(bundleEntryComponentList);
    }

    @NotNull
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
     * @param responseResource FHIR Resource result returned byt the HTTPResponse
     * @return String content of the result Bundle
     */
    private Bundle postProcessModeListEntries(IBaseResource responseResource) {

        Bundle requestBundle = null;

        if (responseResource instanceof ListResource
                && ((ListResource) responseResource).hasEntry()) {

            requestBundle =
                    processListEntriesGatewayModeByListResource((ListResource) responseResource);

        } else if (responseResource instanceof Bundle) {

            requestBundle = processListEntriesGatewayModeByBundle(responseResource);
        }

        return fhirR4Client.transaction().withBundle(requestBundle).execute();
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

        sb.append(org.smartregister.fhir.gateway.plugins.Constants.TAG_SEARCH_PARAM);
        sb.append(org.smartregister.fhir.gateway.plugins.Constants.Literals.EQUALS);

        if (org.smartregister.fhir.gateway.plugins.Constants.LOCATION.equals(syncStrategy)) {
            addTags(
                    org.smartregister.fhir.gateway.plugins.Constants.LOCATION_TAG_URL,
                    syncStrategyIds.get(syncStrategy),
                    map,
                    sb);
        } else if (org.smartregister.fhir.gateway.plugins.Constants.ORGANIZATION.equals(
                syncStrategy)) {
            addTags(
                    org.smartregister.fhir.gateway.plugins.Constants.ORGANISATION_TAG_URL,
                    syncStrategyIds.get(syncStrategy),
                    map,
                    sb);
        } else if (org.smartregister.fhir.gateway.plugins.Constants.CARE_TEAM.equals(
                syncStrategy)) {
            addTags(
                    org.smartregister.fhir.gateway.plugins.Constants.CARE_TEAM_TAG_URL,
                    syncStrategyIds.get(syncStrategy),
                    map,
                    sb);
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
                    != (TAG_SEARCH_PARAM
                                    + org.smartregister.fhir.gateway.plugins.Constants.Literals
                                            .EQUALS)
                            .length()) {
                urlStringBuilder.append(PARAM_VALUES_SEPARATOR);
            }

            map.put(tagUrl, values.toArray(new String[0]));

            int i = 0;
            for (String tagValue : values) {
                urlStringBuilder.append(tagUrl);
                urlStringBuilder.append(CODE_URL_VALUE_SEPARATOR);
                urlStringBuilder.append(tagValue);

                if (i != len - 1) {
                    urlStringBuilder.append(PARAM_VALUES_SEPARATOR);
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
            String[] sections =
                    requestPath.split(com.google.fhir.gateway.ProxyConstants.HTTP_URL_SEPARATOR);

            return sections.length == 1 || (sections.length == 2 && TextUtils.isEmpty(sections[1]));
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

    public static final class Constants {
        public static final String FHIR_GATEWAY_MODE = "fhir-gateway-mode";
        public static final String LIST_ENTRIES = "list-entries";
        public static final String ROLE_SUPERVISOR = "SUPERVISOR";
        public static final String ENDPOINT_PRACTITIONER_DETAILS = "practitioner-details";
        public static final String SYNC_STRATEGY_LOCATION = "Location";
    }
}
