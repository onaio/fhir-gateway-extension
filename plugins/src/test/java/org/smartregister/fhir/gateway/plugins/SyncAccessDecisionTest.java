package org.smartregister.fhir.gateway.plugins;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Patient;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.smartregister.fhir.gateway.plugins.helper.PractitionerDetailsEndpointHelper;
import org.smartregister.fhir.gateway.plugins.utils.TestUtil;
import org.smartregister.helpers.LocationHelper;

import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.fhir.gateway.interfaces.RequestDetailsReader;
import com.google.fhir.gateway.interfaces.RequestMutation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ITransaction;
import ca.uhn.fhir.rest.gclient.ITransactionTyped;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;

@RunWith(MockitoJUnitRunner.class)
public class SyncAccessDecisionTest {

    private final List<String> locationIds = new ArrayList<>();

    private final List<String> careTeamIds = new ArrayList<>();

    private final List<String> organisationIds = new ArrayList<>();

    private final List<String> userRoles = new ArrayList<>();

    private final List<String> relatedEntityLocationIds = new ArrayList<>();

    private SyncAccessDecision testInstance;

    private static @NotNull RequestDetails getRequestDetails(String[] locations) {
        Map<String, String[]> parameters = new HashMap<>();

        // empty string
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, locations);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");
        requestDetails.setParameters(parameters);
        return requestDetails;
    }

    @Test
    public void preProcessShouldAddLocationIdFiltersWhenUserIsAssignedToLocationsOnly()
            throws IOException {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        locationIds.add("locationid12");
        locationIds.add("locationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : locationIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
        }
        assert mutatedRequest != null;
        Assert.assertTrue(
                mutatedRequest
                        .getAdditionalQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        locationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_LOCATION_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param :
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void preProcessWhenNotOneClientRoleIsAddedShouldThrowError() throws IOException {
        // More than one
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        userRoles.add(Constants.ROLE_WEB_CLIENT);
        locationIds.add("locationid12");
        locationIds.add("locationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RuntimeException firstException =
                Assert.assertThrows(
                        RuntimeException.class,
                        () -> {
                            testInstance.getRequestMutation(
                                    new TestRequestDetailsToReader(requestDetails));
                        });

        Assert.assertTrue(
                firstException
                        .getMessage()
                        .contains(
                                "User must have at least one and at most one of these client"
                                        + " roles"));

        // less than one
        userRoles.remove(Constants.ROLE_ANDROID_CLIENT);
        userRoles.remove(Constants.ROLE_WEB_CLIENT);
        RuntimeException secondException =
                Assert.assertThrows(
                        RuntimeException.class,
                        () -> {
                            testInstance.getRequestMutation(
                                    new TestRequestDetailsToReader(requestDetails));
                        });

        Assert.assertTrue(
                secondException
                        .getMessage()
                        .contains(
                                "User must have at least one and at most one of these client"
                                        + " roles"));
    }

    @Test
    public void
            requestMutationWhenUserIsAssignedToRelatedEntityLocationIdsShouldAddAssignedRelatedEntityLocationIdsFilters()
                    throws IOException {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        relatedEntityLocationIds.add("relocationid12");
        relatedEntityLocationIds.add("relocationid2");
        testInstance =
                createSyncAccessDecisionTestInstance(
                        Constants.SyncStrategy.RELATED_ENTITY_LOCATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : relatedEntityLocationIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
        }
        // Verify that both RELATED_ENTITY_LOCATION tag and location-lineage tag are used
        // when using RELATED_ENTITY_LOCATION sync strategy
        List<String> tagParams =
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM);
        Assert.assertNotNull("Tag parameters should be present", tagParams);
        Assert.assertFalse("Tag parameters should not be empty", tagParams.isEmpty());

        // Check that RELATED_ENTITY_LOCATION tag is present
        boolean hasRelatedEntityTag =
                tagParams.stream()
                        .anyMatch(
                                param -> param.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));
        Assert.assertTrue("Query should contain RELATED_ENTITY_LOCATION tag", hasRelatedEntityTag);

        // Check that location-lineage tag is also present
        boolean hasLocationLineageTag =
                tagParams.stream()
                        .anyMatch(
                                param ->
                                        param.contains(
                                                Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));
        Assert.assertTrue("Query should contain location-lineage tag", hasLocationLineageTag);

        // Verify that both tag systems are present with the location IDs
        for (String locationId : relatedEntityLocationIds) {
            String relatedEntityTagPattern =
                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + locationId;
            String locationLineageTagPattern =
                    Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + locationId;

            boolean hasRelatedEntityTagForLocation =
                    tagParams.stream().anyMatch(param -> param.contains(relatedEntityTagPattern));
            boolean hasLocationLineageTagForLocation =
                    tagParams.stream().anyMatch(param -> param.contains(locationLineageTagPattern));

            Assert.assertTrue(
                    "Query should contain RELATED_ENTITY_LOCATION tag for location: " + locationId,
                    hasRelatedEntityTagForLocation);
            Assert.assertTrue(
                    "Query should contain location-lineage tag for location: " + locationId,
                    hasLocationLineageTagForLocation);
        }

        for (String param : tagParams) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void
            requestMutationWhenUserSelectedRelatedEntityLocationIdsShouldAddSelectedRelatedEntityLocationIdsFilters()
                    throws IOException {
        try (MockedStatic<PractitionerDetailsEndpointHelper> mockPractitionerDetailsEndpointHelper =
                Mockito.mockStatic(PractitionerDetailsEndpointHelper.class)) {
            userRoles.add(Constants.ROLE_ANDROID_CLIENT);
            Set<String> selectedRelatedEntityLocationIds = new HashSet<>();
            String srelocationid1 = "srelocationid1";
            String srelocationid2 = "srelocationid2";

            selectedRelatedEntityLocationIds.add(srelocationid1);
            selectedRelatedEntityLocationIds.add(srelocationid2);

            String relatedEntitySearchTagValues =
                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid1
                            + Constants.PARAM_VALUES_SEPARATOR
                            + Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid2;
            String locationLineageSearchTagValues =
                    Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid1
                            + Constants.PARAM_VALUES_SEPARATOR
                            + Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid2;
            relatedEntityLocationIds.add("relocationid1");
            relatedEntityLocationIds.add("relocationid2");
            testInstance =
                    createSyncAccessDecisionTestInstance(
                            Constants.SyncStrategy.RELATED_ENTITY_LOCATION);

            mockPractitionerDetailsEndpointHelper
                    .when(
                            () ->
                                    PractitionerDetailsEndpointHelper.getAttributedLocations(
                                            Mockito.anyList()))
                    .thenReturn(selectedRelatedEntityLocationIds);
            mockPractitionerDetailsEndpointHelper
                    .when(
                            () ->
                                    PractitionerDetailsEndpointHelper.createSearchTagValues(
                                            Mockito.any()))
                    .thenAnswer(
                            invocation -> {
                                @SuppressWarnings("unchecked")
                                Map.Entry<String, String[]> entry =
                                        (Map.Entry<String, String[]>) invocation.getArgument(0);
                                // Return appropriate value based on tag system
                                if (Constants.DEFAULT_RELATED_ENTITY_TAG_URL.equals(
                                        entry.getKey())) {
                                    return relatedEntitySearchTagValues;
                                } else if (Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY.equals(
                                        entry.getKey())) {
                                    return locationLineageSearchTagValues;
                                }
                                // Fallback to related entity tag format
                                return relatedEntitySearchTagValues;
                            });

            RequestDetails requestDetails =
                    getRequestDetails(new String[] {"srelocationid1", "srelocationid2"});
            userRoles.add(Constants.ROLE_ALL_LOCATIONS);

            RequestMutation mutatedRequest =
                    testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

            // Verify that both RELATED_ENTITY_LOCATION tag and location-lineage tag are used
            // when using RELATED_ENTITY_LOCATION sync strategy
            List<String> tagParams =
                    mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM);
            Assert.assertNotNull("Tag parameters should be present", tagParams);
            Assert.assertFalse("Tag parameters should not be empty", tagParams.isEmpty());

            // The result should contain both tag systems (RELATED_ENTITY_LOCATION and
            // location-lineage)
            // Since we're mocking createSearchTagValues to return the same value for both,
            // we should see both tag systems in the result
            String actualTagParam = tagParams.get(0);

            // Verify that RELATED_ENTITY_LOCATION tag is present
            Assert.assertTrue(
                    "Query should contain RELATED_ENTITY_LOCATION tag",
                    actualTagParam.contains(Constants.DEFAULT_RELATED_ENTITY_TAG_URL));

            // Verify that location-lineage tag is also present
            Assert.assertTrue(
                    "Query should contain location-lineage tag",
                    actualTagParam.contains(Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY));

            // Verify that both tag systems are present with the location IDs
            for (String locationId : selectedRelatedEntityLocationIds) {
                String relatedEntityTagPattern =
                        Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                                + Constants.CODE_URL_VALUE_SEPARATOR
                                + locationId;
                String locationLineageTagPattern =
                        Constants.Meta.Tag.SYSTEM_LOCATION_HIERARCHY
                                + Constants.CODE_URL_VALUE_SEPARATOR
                                + locationId;

                Assert.assertTrue(
                        "Query should contain RELATED_ENTITY_LOCATION tag for location: "
                                + locationId,
                        actualTagParam.contains(relatedEntityTagPattern));
                Assert.assertTrue(
                        "Query should contain location-lineage tag for location: " + locationId,
                        actualTagParam.contains(locationLineageTagPattern));
            }

            Collections.reverse(relatedEntityLocationIds);
            // The relatedEntityLocationIds are internal IDs, not the selected sync location IDs,
            // so they should not be in the tag parameters
            Assert.assertFalse(
                    mutatedRequest
                            .getAdditionalQueryParams()
                            .get(Constants.TAG_SEARCH_PARAM)
                            .get(0)
                            .contains(
                                    StringUtils.join(
                                            relatedEntityLocationIds,
                                            Constants.PARAM_VALUES_SEPARATOR
                                                    + Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                                                    + Constants.CODE_URL_VALUE_SEPARATOR)));
        }
    }

    private String searchTagHelper(String tag, Set<String> values) {
        return values.stream()
                .sorted(Collections.reverseOrder())
                .map(it -> tag + it)
                .collect(Collectors.joining(","));
    }

    @Test
    public void requestMutationWhenLocationUuidAreEmptyShouldNotError() {
        try (MockedStatic<PractitionerDetailsEndpointHelper> mockPractitionerDetailsEndpointHelper =
                Mockito.mockStatic(PractitionerDetailsEndpointHelper.class)) {
            userRoles.add(Constants.ROLE_ANDROID_CLIENT);
            Set<String> selectedRelatedEntityLocationIds = new HashSet<>();
            String srelocationid1 = "";
            String srelocationid2 = " ";

            selectedRelatedEntityLocationIds.add(srelocationid1);
            selectedRelatedEntityLocationIds.add(srelocationid2);

            String searchTagValues =
                    Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid1
                            + Constants.PARAM_VALUES_SEPARATOR
                            + Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                            + Constants.CODE_URL_VALUE_SEPARATOR
                            + srelocationid2;
            relatedEntityLocationIds.add("relocationid1");
            relatedEntityLocationIds.add("relocationid2");
            testInstance =
                    createSyncAccessDecisionTestInstance(
                            Constants.SyncStrategy.RELATED_ENTITY_LOCATION);

            mockPractitionerDetailsEndpointHelper
                    .when(
                            () ->
                                    PractitionerDetailsEndpointHelper.getAttributedLocations(
                                            Mockito.anyList()))
                    .thenReturn(selectedRelatedEntityLocationIds);
            mockPractitionerDetailsEndpointHelper
                    .when(
                            () ->
                                    PractitionerDetailsEndpointHelper.createSearchTagValues(
                                            Mockito.any()))
                    .thenReturn(searchTagValues);

            RequestDetails requestDetails = getRequestDetails(new String[] {});
            userRoles.add(Constants.ROLE_ALL_LOCATIONS);

            testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));
        }
    }

    @Test
    public void preProcessShouldAddCareTeamIdFiltersWhenUserIsAssignedToCareTeamsOnly()
            throws IOException {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        careTeamIds.add("careteamid1");
        careTeamIds.add("careteamid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.CARE_TEAM);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : careTeamIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
        }

        Assert.assertTrue(
                mutatedRequest
                        .getAdditionalQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        careTeamIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_CARE_TEAM_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param :
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_LOCATION_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void preProcessShouldAddOrganisationIdFiltersWhenUserIsAssignedToOrganisationsOnly() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.ORGANIZATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : careTeamIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
            Assert.assertTrue(
                    mutatedRequest
                            .getAdditionalQueryParams()
                            .get(Constants.TAG_SEARCH_PARAM)
                            .contains(
                                    Constants.DEFAULT_ORGANISATION_TAG_URL
                                            + Constants.CODE_URL_VALUE_SEPARATOR
                                            + locationId));
        }

        for (String param :
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_LOCATION_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
        }
    }

    @Test
    public void preProcessShouldAddFiltersWhenResourceNotInSyncFilterIgnoredResourcesFile() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.ORGANIZATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : organisationIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
            Assert.assertEquals(1, mutatedRequest.getAdditionalQueryParams().size());
        }
        Assert.assertTrue(
                mutatedRequest
                        .getAdditionalQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        organisationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_ORGANISATION_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));
    }

    @Test
    public void preProcessShouldSkipAddingFiltersWhenResourceInSyncFilterIgnoredResourcesFile() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.ORGANIZATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Questionnaire");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Questionnaire");
        requestDetails.setRequestPath("Questionnaire");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : organisationIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
            Assert.assertNull(mutatedRequest);
        }
    }

    @Test
    public void
            preProcessShouldSkipAddingFiltersWhenSearchResourceByIdsInSyncFilterIgnoredResourcesFile() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.ORGANIZATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("StructureMap");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        List<String> queryStringParamValues = Arrays.asList("1000", "2000", "3000");
        requestDetails.setCompleteUrl(
                "https://smartregister.org/fhir/StructureMap?_id="
                        + StringUtils.join(
                                queryStringParamValues, Constants.PARAM_VALUES_SEPARATOR));
        Assert.assertEquals(
                "https://smartregister.org/fhir/StructureMap?_id=1000,2000,3000",
                requestDetails.getCompleteUrl());
        requestDetails.setRequestPath("StructureMap");

        Map<String, String[]> params = Maps.newHashMap();
        params.put(
                "_id",
                new String[] {
                    StringUtils.join(queryStringParamValues, Constants.PARAM_VALUES_SEPARATOR)
                });
        requestDetails.setParameters(params);

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        Assert.assertNull(mutatedRequest);
    }

    @Test
    public void
            preProcessShouldAddFiltersWhenSearchResourceByIdsDoNotMatchSyncFilterIgnoredResources() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.ORGANIZATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("StructureMap");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        List<String> queryStringParamValues = Arrays.asList("1000", "2000");
        requestDetails.setCompleteUrl(
                "https://smartregister.org/fhir/StructureMap?_id="
                        + StringUtils.join(
                                queryStringParamValues, Constants.PARAM_VALUES_SEPARATOR));
        Assert.assertEquals(
                "https://smartregister.org/fhir/StructureMap?_id=1000,2000",
                requestDetails.getCompleteUrl());
        requestDetails.setRequestPath("StructureMap");

        Map<String, String[]> params = Maps.newHashMap();
        params.put(
                "_id",
                new String[] {
                    StringUtils.join(queryStringParamValues, Constants.PARAM_VALUES_SEPARATOR)
                });
        requestDetails.setParameters(params);

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        List<String> searchParamArrays =
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM);
        Assert.assertNotNull(searchParamArrays);

        Assert.assertTrue(
                searchParamArrays
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        organisationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_ORGANISATION_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));
    }

    @Test(expected = RuntimeException.class)
    public void preprocessShouldThrowRuntimeExceptionWhenNoSyncStrategyFilterIsProvided() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        testInstance = createSyncAccessDecisionTestInstance(null);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setRequestPath("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");

        // Call the method under testing
        testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));
    }

    @Test
    public void testPostProcessWithListModeHeaderShouldFetchListEntriesBundle() throws IOException {
        locationIds.add("Location-1");
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));

        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);
        testInstance.setFhirR4Client(iGenericClient);
        testInstance.setFhirR4Context(fhirR4Context);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
        resultBundle.setId("bundle-result-id");

        Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        testInstance.setFhirR4Context(fhirR4Context);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn(SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES);

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

        TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
        String fhirServerBase = "http://test:8080/fhir";
        Mockito.when(requestDetailsSpy.getFhirServerBase()).thenReturn(fhirServerBase);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");
        String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();

        // Verify modified request to the server
        Assert.assertNotNull(requestBundle);
        Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
        List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();
        Assert.assertEquals(2, requestBundleEntries.size());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(1).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-2", requestBundleEntries.get(1).getRequest().getUrl());

        // Verify returned result content from the server request
        Assert.assertNotNull(resultContent);
        Assert.assertEquals(
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":2,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessWithListModeQueryParamShouldFetchListEntriesBundle()
            throws IOException {
        locationIds.add("Location-1");
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));

        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);
        testInstance.setFhirR4Client(iGenericClient);
        testInstance.setFhirR4Context(fhirR4Context);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
        resultBundle.setId("bundle-result-id");

        Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        testInstance.setFhirR4Context(fhirR4Context);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

        Map<String, String[]> params = new HashMap<>();
        params.put(
                Constants.Header.MODE,
                new String[] {SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES});

        Mockito.when(requestDetailsSpy.getParameters()).thenReturn(params);

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

        TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);
        String fhirServerBase = "http://test:8080/fhir";
        Mockito.when(requestDetailsSpy.getFhirServerBase()).thenReturn(fhirServerBase);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");
        String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();

        // Verify modified request to the server
        Assert.assertNotNull(requestBundle);
        Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
        List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();
        Assert.assertEquals(2, requestBundleEntries.size());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(1).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-2", requestBundleEntries.get(1).getRequest().getUrl());

        // Verify returned result content from the server request
        Assert.assertNotNull(resultContent);
        Assert.assertEquals(
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":2,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?mode=list-entries\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessWithoutListModeHeaderShouldShouldReturnNull() throws IOException {
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);
        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn("");

        String resultContent =
                testInstance.postProcess(requestDetailsSpy, Mockito.mock(HttpResponse.class));

        // Verify no special Post-Processing happened
        Assert.assertNull(resultContent);
    }

    @Test
    public void testPostProcessWithListModeHeaderSearchByTagShouldFetchListEntriesBundle()
            throws IOException {
        locationIds.add("Location-1");
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));

        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
        resultBundle.setId("bundle-result-id");

        Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        testInstance.setFhirR4Context(fhirR4Context);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn(SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES);

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        ListResource listResource =
                (ListResource) FhirContext.forR4().newJsonParser().parseResource(testListJson);

        Bundle bundle = new Bundle();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setResource(listResource);
        bundle.setType(Bundle.BundleType.BATCHRESPONSE);
        bundle.setEntry(List.of(bundleEntryComponent));

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

        TestUtil.setUpFhirResponseMock(
                fhirResponseMock,
                FhirContext.forR4().newJsonParser().encodeResourceToString(bundle));

        testInstance.setFhirR4Client(iGenericClient);
        testInstance.setFhirR4Context(FhirContext.forR4());
        String fhirServerBase = "http://test:8080/fhir";
        Mockito.when(requestDetailsSpy.getFhirServerBase()).thenReturn(fhirServerBase);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");
        String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();

        // Verify modified request to the server
        Assert.assertNotNull(requestBundle);
        Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
        List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();
        Assert.assertEquals(2, requestBundleEntries.size());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(1).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-2", requestBundleEntries.get(1).getRequest().getUrl());

        // Verify returned result content from the server request
        Assert.assertNotNull(resultContent);
        Assert.assertEquals(
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":2,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessWithListModeHeaderPaginateEntriesBundle() throws IOException {
        locationIds.add("Location-1");
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));

        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);
        testInstance.setFhirR4Client(iGenericClient);
        testInstance.setFhirR4Context(fhirR4Context);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
        resultBundle.setId("bundle-result-id");

        Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        testInstance.setFhirR4Context(fhirR4Context);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn(SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES);

        Map<String, String[]> params = new HashMap<>();
        params.put("_count", new String[] {"1"});
        params.put("_page", new String[] {"1"});

        String fhirServerBase = "http://test:8080/fhir";

        Mockito.when(requestDetailsSpy.getParameters()).thenReturn(params);
        Mockito.when(requestDetailsSpy.getFhirServerBase()).thenReturn(fhirServerBase);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

        TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);

        String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();

        // Verify modified request to the server
        Assert.assertNotNull(requestBundle);
        Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
        List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();

        // Only one returned one _page = 1 and _count = 1
        Assert.assertEquals(1, requestBundleEntries.size());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-1", requestBundleEntries.get(0).getRequest().getUrl());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());

        // Verify returned result content from the server request has pagination links
        Assert.assertNotNull(resultContent);
        Assert.assertEquals(
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":2,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?_page=1&_count=1\"},{\"relation\":\"next\",\"url\":\"http://test:8080/fhir/List?_page=2&_count=1\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessWithListModeHeaderSearchByTagPaginateEntriesBundle()
            throws IOException {
        locationIds.add("Location-1");
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));

        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);

        Bundle resultBundle = new Bundle();
        resultBundle.setType(Bundle.BundleType.BATCHRESPONSE);
        resultBundle.setId("bundle-result-id");

        Mockito.when(iClientExecutable.execute()).thenReturn(resultBundle);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        testInstance.setFhirR4Context(fhirR4Context);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);

        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn(SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES);

        Map<String, String[]> params = new HashMap<>();
        params.put("_count", new String[] {"1"});
        params.put("_page", new String[] {"2"});
        String fhirServerBase = "http://test:8080/fhir";

        Mockito.when(requestDetailsSpy.getParameters()).thenReturn(params);
        Mockito.when(requestDetailsSpy.getFhirServerBase()).thenReturn(fhirServerBase);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        ListResource listResource =
                (ListResource) FhirContext.forR4().newJsonParser().parseResource(testListJson);

        Bundle bundle = new Bundle();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setResource(listResource);
        bundle.setType(Bundle.BundleType.BATCHRESPONSE);
        bundle.setEntry(List.of(bundleEntryComponent));

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);

        TestUtil.setUpFhirResponseMock(
                fhirResponseMock,
                FhirContext.forR4().newJsonParser().encodeResourceToString(bundle));

        testInstance.setFhirR4Client(iGenericClient);
        testInstance.setFhirR4Context(FhirContext.forR4());
        String resultContent = testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();

        Assert.assertNotNull(requestBundle);
        Assert.assertEquals(Bundle.BundleType.BATCH, requestBundle.getType());
        List<Bundle.BundleEntryComponent> requestBundleEntries = requestBundle.getEntry();

        // Only one bundle is returned based on the _page and _count params provided above
        Assert.assertEquals(1, requestBundleEntries.size());

        Assert.assertEquals(
                Bundle.HTTPVerb.GET, requestBundleEntries.get(0).getRequest().getMethod());
        Assert.assertEquals(
                "Group/proxy-list-entry-id-2", requestBundleEntries.get(0).getRequest().getUrl());

        // Verify returned result content from the server request, has pagination links
        Assert.assertNotNull(resultContent);
        Assert.assertEquals(
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":2,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?_page=2&_count=1\"},{\"relation\":\"previous\",\"url\":\"http://test:8080/fhir/List?_page=1&_count=1\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessCallsUpdateLocationLineage() throws IOException {
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));
        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);

        testInstance.setFhirR4Context(fhirR4Context);
        testInstance.setFhirR4Client(iGenericClient);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);
        RequestTypeEnum requestTypeEnumMock = Mockito.mock(RequestTypeEnum.class);

        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("Location/123");
        Mockito.when(requestDetailsSpy.getRequestType()).thenReturn(requestTypeEnumMock);
        Mockito.when(requestTypeEnumMock.name()).thenReturn("POST");
        Mockito.when(requestDetailsSpy.getResourceName())
                .thenReturn(Constants.SyncStrategy.LOCATION);

        Location location = new Location();
        location.setId("Location/123");

        String responseJson = FhirContext.forR4().newJsonParser().encodeResourceToString(location);
        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
        TestUtil.setUpFhirResponseMock(fhirResponseMock, responseJson);
        Mockito.doReturn("123")
                .when(testInstance)
                .getLocationId(Mockito.anyString(), Mockito.any());

        try (MockedStatic<LocationHelper> locationHelperMock =
                Mockito.mockStatic(LocationHelper.class)) {
            locationHelperMock
                    .when(() -> LocationHelper.updateLocationLineage(any(), any()))
                    .thenReturn(location);

            testInstance.postProcess(requestDetailsSpy, fhirResponseMock);
            locationHelperMock.verify(
                    () -> LocationHelper.updateLocationLineage(eq(iGenericClient), eq("123")));
        }
    }

    @Test
    public void testGetLocationId() {
        String requestPath = "Location/123";
        testInstance =
                Mockito.spy(createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION));
        FhirContext fhirR4Context = mock(FhirContext.class);
        IGenericClient iGenericClient = mock(IGenericClient.class);

        testInstance.setFhirR4Context(fhirR4Context);
        testInstance.setFhirR4Client(iGenericClient);

        Location location = new Location();
        location.setId("Location/123");
        String validJson = FhirContext.forR4().newJsonParser().encodeResourceToString(location);

        String locationId = testInstance.getLocationId(requestPath, validJson);
        Assert.assertEquals("123", locationId);

        Patient patient = new Patient();
        patient.setId("Patient/345");
        String validPatientJson =
                FhirContext.forR4().newJsonParser().encodeResourceToString(patient);
        String locId = testInstance.getLocationId(requestPath, validPatientJson);
        Assert.assertEquals("123", locId);
    }

    @Test
    public void preProcessWhenRequestIsAnOperationRequestShouldAddFilters() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        locationIds.add("locationid12");
        locationIds.add("locationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Location");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Location/_search");
        requestDetails.setRequestPath("Location/_search");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        for (String locationId : locationIds) {
            Assert.assertFalse(requestDetails.getCompleteUrl().contains(locationId));
            Assert.assertFalse(requestDetails.getRequestPath().contains(locationId));
        }
        Assert.assertTrue(
                mutatedRequest
                        .getAdditionalQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        locationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_LOCATION_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param :
                mutatedRequest.getAdditionalQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void getRequestMutationMarksFilterModeLinageQueryparamForRemoval() {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);

        locationIds.add("locationid12");
        locationIds.add("locationid2");

        testInstance = createSyncAccessDecisionTestInstance(Constants.SyncStrategy.LOCATION);

        RequestDetails requestDetails = new ServletRequestDetails();
        requestDetails.setRequestType(RequestTypeEnum.GET);
        requestDetails.setRestOperationType(RestOperationTypeEnum.SEARCH_TYPE);
        requestDetails.setResourceName("Patient");
        requestDetails.setFhirServerBase("https://smartregister.org/fhir");
        requestDetails.setCompleteUrl("https://smartregister.org/fhir/Patient");
        requestDetails.setRequestPath("Patient");

        RequestMutation mutatedRequest =
                testInstance.getRequestMutation(new TestRequestDetailsToReader(requestDetails));

        Assert.assertNotNull(mutatedRequest);
        Assert.assertTrue(
                mutatedRequest.getDiscardQueryParams().contains(Constants.FILTER_MODE_LINEAGE));
    }

    @Test
    public void createBundleEntryComponentShouldCopyRequestDetails() {
        Bundle.BundleEntryComponent entryComponent =
                SyncAccessDecision.createBundleEntryComponent(
                        Bundle.HTTPVerb.POST, "Observation/123", "W/\"etag\"");

        Assert.assertNotNull(entryComponent);
        Assert.assertNotNull(entryComponent.getRequest());
        Assert.assertEquals(Bundle.HTTPVerb.POST, entryComponent.getRequest().getMethod());
        Assert.assertEquals("Observation/123", entryComponent.getRequest().getUrl());
        Assert.assertEquals("W/\"etag\"", entryComponent.getRequest().getIfMatch());
    }

    @Test
    public void postProcessShouldExcludeClientSuppliedSyncParamsFromRelatedEntityRequests()
            throws IOException {
        userRoles.add(Constants.ROLE_ANDROID_CLIENT);
        for (int i = 0; i < 25; i++) {
            relatedEntityLocationIds.add("rel-location-" + i);
        }

        testInstance =
                Mockito.spy(
                        createSyncAccessDecisionTestInstance(
                                Constants.SyncStrategy.RELATED_ENTITY_LOCATION));

        IGenericClient iGenericClient = mock(IGenericClient.class);
        ITransaction iTransaction = mock(ITransaction.class);
        @SuppressWarnings("unchecked")
        ITransactionTyped<Bundle> iClientExecutable = mock(ITransactionTyped.class);

        FhirContext fhirR4Context = FhirContext.forR4();
        testInstance.setFhirR4Context(fhirR4Context);
        testInstance.setFhirR4Client(iGenericClient);

        Mockito.when(iGenericClient.transaction()).thenReturn(iTransaction);
        Mockito.when(iTransaction.withBundle(any(Bundle.class))).thenReturn(iClientExecutable);
        Mockito.when(iGenericClient.getFhirContext()).thenReturn(fhirR4Context);

        Bundle transactionResult = new Bundle();
        transactionResult.setType(Bundle.BundleType.BATCHRESPONSE);
        Mockito.when(iClientExecutable.execute()).thenReturn(transactionResult);

        ArgumentCaptor<Bundle> bundleArgumentCaptor = ArgumentCaptor.forClass(Bundle.class);

        Map<String, String[]> params = new HashMap<>();
        params.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"shouldNotAppear"});
        params.put(Constants.TAG_SEARCH_PARAM, new String[] {"preExistingTag"});
        params.put("status", new String[] {"active"});

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);
        Mockito.when(requestDetailsSpy.getParameters()).thenReturn(params);
        Mockito.when(requestDetailsSpy.getRequestPath()).thenReturn("List");
        Mockito.when(requestDetailsSpy.getRequestType()).thenReturn(RequestTypeEnum.GET);
        Mockito.when(requestDetailsSpy.getHeader(Constants.Header.FHIR_GATEWAY_MODE))
                .thenReturn(null);

        HttpResponse fhirResponseMock =
                Mockito.mock(HttpResponse.class, Answers.RETURNS_DEEP_STUBS);
        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);
        TestUtil.setUpFhirResponseMock(fhirResponseMock, testListJson);

        testInstance.postProcess(requestDetailsSpy, fhirResponseMock);

        Mockito.verify(iTransaction).withBundle(bundleArgumentCaptor.capture());
        Bundle requestBundle = bundleArgumentCaptor.getValue();
        Assert.assertNotNull(requestBundle);
        Assert.assertFalse(requestBundle.getEntry().isEmpty());

        for (Bundle.BundleEntryComponent entryComponent : requestBundle.getEntry()) {
            String requestUrl = entryComponent.getRequest().getUrl();
            Assert.assertTrue(requestUrl.contains("status=active"));
            Assert.assertFalse(requestUrl.contains("preExistingTag"));
            Assert.assertFalse(requestUrl.contains("shouldNotAppear"));
        }
    }

    @After
    public void cleanUp() {
        locationIds.clear();
        careTeamIds.clear();
        organisationIds.clear();
        relatedEntityLocationIds.clear();
        userRoles.clear();
    }

    private SyncAccessDecision createSyncAccessDecisionTestInstance(String syncStrategy) {
        FhirContext fhirR4Context = FhirContext.forR4();

        Map<String, List<String>> syncStrategyIds = new HashMap<>();
        syncStrategyIds.put(Constants.SyncStrategy.LOCATION, locationIds);
        syncStrategyIds.put(Constants.SyncStrategy.CARE_TEAM, careTeamIds);
        syncStrategyIds.put(Constants.SyncStrategy.ORGANIZATION, organisationIds);
        syncStrategyIds.put(
                Constants.SyncStrategy.RELATED_ENTITY_LOCATION, relatedEntityLocationIds);

        SyncAccessDecision accessDecision =
                new SyncAccessDecision(
                        fhirR4Context,
                        "sample-keycloak-id",
                        true,
                        syncStrategyIds,
                        syncStrategy,
                        userRoles);

        URL configFileUrl = Resources.getResource("hapi_sync_filter_ignored_queries.json");
        SyncAccessDecision.IgnoredResourcesConfig skippedDataFilterConfig =
                accessDecision.getIgnoredResourcesConfigFileConfiguration(configFileUrl.getPath());
        accessDecision.setSkippedResourcesConfig(skippedDataFilterConfig);
        return accessDecision;
    }
}
