package org.smartregister.fhir.gateway.plugins;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ListResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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

    private List<String> locationIds = new ArrayList<>();

    private List<String> careTeamIds = new ArrayList<>();

    private List<String> organisationIds = new ArrayList<>();

    private List<String> userRoles = new ArrayList<>();

    private List<String> relatedEntityLocationIds = new ArrayList<>();

    private SyncAccessDecision testInstance;

    @Test
    public void preProcessShouldAddLocationIdFiltersWhenUserIsAssignedToLocationsOnly()
            throws IOException {
        locationIds.add("locationid12");
        locationIds.add("locationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.LOCATION);

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
        Assert.assertTrue(
                mutatedRequest
                        .getQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        locationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_LOCATION_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param : mutatedRequest.getQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void
            preProcessShouldAddRelatedEntityLocationIdsFiltersWhenUserIsAssignedToRelatedEntityLocationIdsOnly()
                    throws IOException {
        relatedEntityLocationIds.add("relocationid12");
        relatedEntityLocationIds.add("relocationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.RELATED_ENTITY_LOCATION);

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
        Assert.assertTrue(
                mutatedRequest
                        .getQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        relatedEntityLocationIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_RELATED_ENTITY_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param : mutatedRequest.getQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void preProcessShouldAddCareTeamIdFiltersWhenUserIsAssignedToCareTeamsOnly()
            throws IOException {
        careTeamIds.add("careteamid1");
        careTeamIds.add("careteamid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.CARE_TEAM);

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
                        .getQueryParams()
                        .get(Constants.TAG_SEARCH_PARAM)
                        .get(0)
                        .contains(
                                StringUtils.join(
                                        careTeamIds,
                                        Constants.PARAM_VALUES_SEPARATOR
                                                + Constants.DEFAULT_CARE_TEAM_TAG_URL
                                                + Constants.CODE_URL_VALUE_SEPARATOR)));

        for (String param : mutatedRequest.getQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_LOCATION_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_ORGANISATION_TAG_URL));
        }
    }

    @Test
    public void preProcessShouldAddOrganisationIdFiltersWhenUserIsAssignedToOrganisationsOnly()
            throws IOException {
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.ORGANIZATION);

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
                            .getQueryParams()
                            .get(Constants.TAG_SEARCH_PARAM)
                            .contains(
                                    Constants.DEFAULT_ORGANISATION_TAG_URL
                                            + Constants.CODE_URL_VALUE_SEPARATOR
                                            + locationId));
        }

        for (String param : mutatedRequest.getQueryParams().get(Constants.TAG_SEARCH_PARAM)) {
            Assert.assertFalse(param.contains(Constants.DEFAULT_LOCATION_TAG_URL));
            Assert.assertFalse(param.contains(Constants.DEFAULT_CARE_TEAM_TAG_URL));
        }
    }

    @Test
    public void preProcessShouldAddFiltersWhenResourceNotInSyncFilterIgnoredResourcesFile() {
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.ORGANIZATION);

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
            Assert.assertEquals(1, mutatedRequest.getQueryParams().size());
        }
        Assert.assertTrue(
                mutatedRequest
                        .getQueryParams()
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
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.ORGANIZATION);

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
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.ORGANIZATION);

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
        organisationIds.add("organizationid1");
        organisationIds.add("organizationid2");
        testInstance = createSyncAccessDecisionTestInstance(Constants.ORGANIZATION);

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
                mutatedRequest.getQueryParams().get(Constants.TAG_SEARCH_PARAM);
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
        testInstance = Mockito.spy(createSyncAccessDecisionTestInstance(Constants.LOCATION));

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

        Mockito.when(
                        requestDetailsSpy.getHeader(
                                SyncAccessDecision.SyncAccessDecisionConstants.FHIR_GATEWAY_MODE))
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
    public void testPostProcessWithoutListModeHeaderShouldShouldReturnNull() throws IOException {
        testInstance = createSyncAccessDecisionTestInstance(Constants.LOCATION);

        RequestDetailsReader requestDetailsSpy = Mockito.mock(RequestDetailsReader.class);
        Mockito.when(
                        requestDetailsSpy.getHeader(
                                SyncAccessDecision.SyncAccessDecisionConstants.FHIR_GATEWAY_MODE))
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
        testInstance = Mockito.spy(createSyncAccessDecisionTestInstance(Constants.LOCATION));

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

        Mockito.when(
                        requestDetailsSpy.getHeader(
                                SyncAccessDecision.SyncAccessDecisionConstants.FHIR_GATEWAY_MODE))
                .thenReturn(SyncAccessDecision.SyncAccessDecisionConstants.LIST_ENTRIES);

        URL listUrl = Resources.getResource("test_list_resource.json");
        String testListJson = Resources.toString(listUrl, StandardCharsets.UTF_8);

        ListResource listResource =
                (ListResource) FhirContext.forR4().newJsonParser().parseResource(testListJson);

        Bundle bundle = new Bundle();
        Bundle.BundleEntryComponent bundleEntryComponent = new Bundle.BundleEntryComponent();
        bundleEntryComponent.setResource(listResource);
        bundle.setType(Bundle.BundleType.BATCHRESPONSE);
        bundle.setEntry(Arrays.asList(bundleEntryComponent));

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
        testInstance = Mockito.spy(createSyncAccessDecisionTestInstance(Constants.LOCATION));

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

        Mockito.when(
                        requestDetailsSpy.getHeader(
                                SyncAccessDecision.SyncAccessDecisionConstants.FHIR_GATEWAY_MODE))
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
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?_page=1&_count=1\"},{\"relation\":\"next\",\"url\":\"http://test:8080/fhir/List?_page=2&_count=1\"}]}",
                resultContent);
    }

    @Test
    public void testPostProcessWithListModeHeaderSearchByTagPaginateEntriesBundle()
            throws IOException {
        locationIds.add("Location-1");
        testInstance = Mockito.spy(createSyncAccessDecisionTestInstance(Constants.LOCATION));

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

        Mockito.when(
                        requestDetailsSpy.getHeader(
                                SyncAccessDecision.SyncAccessDecisionConstants.FHIR_GATEWAY_MODE))
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
        bundle.setEntry(Arrays.asList(bundleEntryComponent));

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
                "{\"resourceType\":\"Bundle\",\"id\":\"bundle-result-id\",\"type\":\"batch-response\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://test:8080/fhir/List?_page=2&_count=1\"},{\"relation\":\"previous\",\"url\":\"http://test:8080/fhir/List?_page=1&_count=1\"}]}",
                resultContent);
    }

    @After
    public void cleanUp() {
        locationIds.clear();
        careTeamIds.clear();
        organisationIds.clear();
        relatedEntityLocationIds.clear();
    }

    private SyncAccessDecision createSyncAccessDecisionTestInstance(String syncStrategy) {
        FhirContext fhirR4Context = FhirContext.forR4();

        Map<String, List<String>> syncStrategyIds = new HashMap<>();
        syncStrategyIds.put(Constants.LOCATION, locationIds);
        syncStrategyIds.put(Constants.CARE_TEAM, careTeamIds);
        syncStrategyIds.put(Constants.ORGANIZATION, organisationIds);
        syncStrategyIds.put(Constants.RELATED_ENTITY_LOCATION, relatedEntityLocationIds);

        SyncAccessDecision accessDecision =
                new SyncAccessDecision(
                        fhirR4Context,
                        "sample-keycloak-id",
                        "sample-application-id",
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
