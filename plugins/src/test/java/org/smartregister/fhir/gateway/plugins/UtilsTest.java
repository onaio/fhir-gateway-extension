package org.smartregister.fhir.gateway.plugins;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.junit.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.impl.GenericClient;
import ca.uhn.fhir.rest.gclient.IGetPage;
import ca.uhn.fhir.rest.gclient.IGetPageTyped;

public class UtilsTest {

    private FhirContext fhirContextMock;
    private GenericClient genericClientMock;

    @Before
    public void setUp() {
        fhirContextMock = Mockito.mock(FhirContext.class);
        IGenericClient clientMock = Mockito.mock(IGenericClient.class);
        genericClientMock = Mockito.mock(GenericClient.class);

        Mockito.when(fhirContextMock.newRestfulGenericClient(Mockito.anyString()))
                .thenReturn(clientMock);
    }

    @After
    public void tearDown() {
        Mockito.clearAllCaches();
    }

    @Test
    public void testCreateEmptyBundle() {
        String requestURL = "http://example.com/fhir/Bundle";
        Bundle result = Utils.createEmptyBundle(requestURL);
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getId());
        Assert.assertEquals(0, result.getTotal());
        Assert.assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
        Assert.assertEquals(1, result.getLink().size());
        Assert.assertEquals(Bundle.LINK_SELF, result.getLink().get(0).getRelation());
        Assert.assertEquals(requestURL, result.getLink().get(0).getUrl());
    }

    @Test
    public void testGetBinaryResourceReferenceWithNullComposition() {
        String result = Utils.getBinaryResourceReference(null);

        Assert.assertEquals("", result);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithEmptySection() {
        Composition composition = new Composition();
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertEquals("", result);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithNoMatchingSection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference().setIdentifier(new Identifier().setValue("otherValue")));
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition);
    }

    @Test
    public void testGetBinaryResourceReferenceWithMatchingSection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Identifier identifier = new Identifier();
        identifier.setValue(Constants.AppConfigJsonKey.APPLICATION);
        Reference reference = new Reference();
        reference.setIdentifier(identifier);
        reference.setReference("Binary/1234");
        sectionComponent.setFocus(reference);
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertEquals("Binary/1234", result);
    }

    @Test
    public void testGetBinaryResourceReferenceWithNullFocus() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(null);
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertNull(result);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testGetBinaryResourceReferenceWithNoIdentifier() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Reference reference = new Reference();
        reference.setReference("Binary/5678");
        sectionComponent.setFocus(reference);
        sectionComponent.setFocus(new Reference());
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition);
    }

    @Test
    public void testGetBinaryResourceReferenceWithNoFocusReference() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition);
        Assert.assertNull(result);
    }

    @Test
    public void testFindSyncStrategyWithNullBinary() {
        String result = Utils.findSyncStrategy(null);
        Assert.assertEquals(org.smartregister.utils.Constants.EMPTY_STRING, result);
    }

    @Test
    public void testFindSyncStrategyWithEmptySyncStrategyArray() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, new JsonArray());
        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));
        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals(org.smartregister.utils.Constants.EMPTY_STRING, result);
    }

    @Test
    public void testFindSyncStrategyWithValidSyncStrategy() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        JsonArray syncStrategyArray = new JsonArray();
        syncStrategyArray.add("PUSH");
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, syncStrategyArray);
        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));

        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals("PUSH", result);
    }

    @Test
    public void testFindSyncStrategyWithMultipleSyncStrategies() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        JsonArray syncStrategyArray = new JsonArray();
        syncStrategyArray.add("PUSH");
        syncStrategyArray.add("PULL");
        jsonObject.add(Constants.AppConfigJsonKey.SYNC_STRATEGY, syncStrategyArray);

        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));

        String result = Utils.findSyncStrategy(binary);
        Assert.assertEquals("PUSH", result);
    }

    @Test
    public void testReadApplicationConfigBinaryResourceWithEmptyResourceId() {
        Binary result = Utils.readApplicationConfigBinaryResource("", fhirContextMock);
        Assert.assertNull(result);
    }

    @Test
    public void testGenerateHashConsistency() throws NoSuchAlgorithmException {
        String input = "consistentTest";
        String hash1 = Utils.generateHash(input);
        String hash2 = Utils.generateHash(input);
        Assert.assertEquals(hash1, hash2);
    }

    @Test
    public void testGenerateHashDifferentInputs() throws NoSuchAlgorithmException {
        String input1 = "inputOne";
        String input2 = "inputTwo";
        String hash1 = Utils.generateHash(input1);
        String hash2 = Utils.generateHash(input2);
        Assert.assertNotEquals(hash1, hash2);
    }

    @Test
    public void testFetchAllBundlePagesAndInject() {
        Bundle firstPageBundle = new Bundle();
        firstPageBundle.setMeta(new Meta().setLastUpdated(new Date()));
        firstPageBundle.addLink().setRelation(Bundle.LINK_NEXT).setUrl("nextPageUrl");

        Bundle secondPageBundle = new Bundle();
        secondPageBundle.setMeta(new Meta().setLastUpdated(new Date()));
        secondPageBundle.addEntry(new Bundle.BundleEntryComponent());

        IGetPage loadPageMock = Mockito.mock(IGetPage.class);
        IGetPageTyped iGetPageTypedMock = Mockito.mock(IGetPageTyped.class);
        Mockito.doReturn(loadPageMock).when(genericClientMock).loadPage();
        Mockito.doReturn(iGetPageTypedMock).when(loadPageMock).next(firstPageBundle);
        Mockito.doReturn(secondPageBundle).when(iGetPageTypedMock).execute();
        Utils.fetchAllBundlePagesAndInject(genericClientMock, firstPageBundle);

        Assert.assertEquals(1, firstPageBundle.getEntry().size());
        Assert.assertNull(firstPageBundle.getLink(Bundle.LINK_NEXT));
        Assert.assertNotNull(firstPageBundle.getMeta().getLastUpdated());
        Mockito.verify(genericClientMock.loadPage(), Mockito.times(1)).next(firstPageBundle);
    }

    @Test
    public void testCleanUpBundleLinksServerBaseUrlMultiplePaginationNextLink() {
        Bundle resultBundle = new Bundle();
        resultBundle
                .addLink()
                .setRelation(Bundle.LINK_NEXT)
                .setUrl(
                        "http://old-base-url:8080/fhir?_getpages=c380a770-4ecc-45fa-b9c4-003c5b37e1f4&_getpagesoffset=2&_count=1&_pretty=true&_bundletype=searchset");

        Mockito.when(genericClientMock.getUrlBase()).thenReturn("http://new-base-url");

        Utils.cleanUpBundlePaginationNextLinkServerBaseUrl(genericClientMock, resultBundle);

        List<Bundle.BundleLinkComponent> links = resultBundle.getLink();

        Bundle.BundleLinkComponent nextLink =
                links.stream()
                        .filter(link -> Bundle.LINK_NEXT.equals(link.getRelation()))
                        .findFirst()
                        .orElse(null);
        Assert.assertNotNull(nextLink);
        Assert.assertEquals(
                "http://new-base-url?_getpages=c380a770-4ecc-45fa-b9c4-003c5b37e1f4&_getpagesoffset=2&_count=1&_pretty=true&_bundletype=searchset",
                nextLink.getUrl());
    }

    @Test
    public void testGenericCleanBaseUrl() {
        String cleanHostUrl =
                Utils.cleanBaseUrl(
                        "http://old-base-url/nextPage?param=value", "http://new-base-url");
        Assert.assertEquals("http://new-base-url/nextPage?param=value", cleanHostUrl);
    }

    @Test
    public void testGenerateSyncStrategyIdsCacheKeyWithSyncLocations() {
        String userId = "user123";
        String syncStrategy = Constants.SyncStrategy.RELATED_ENTITY_LOCATION;
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"location1"});

        MockedStatic<Utils> mockUtils = Mockito.mockStatic(Utils.class);
        mockUtils.when(() -> Utils.generateHash("location1")).thenReturn("hashedLocation1");
        mockUtils.when(() -> Utils.getSortedInput("location1", ",")).thenReturn("location1");

        String result =
                PermissionAccessChecker.generateSyncStrategyIdsCacheKey(
                        userId, syncStrategy, parameters);
        Assert.assertEquals("hashedLocation1", result);
        mockUtils.close();
    }

    @Test
    public void testGenerateSyncStrategyIdsCacheKeyDefaultStrategy() {
        String userId = "user123";
        String syncStrategy = "someOtherStrategy";
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put(Constants.SYNC_LOCATIONS_SEARCH_PARAM, new String[] {"location1"});

        String result =
                PermissionAccessChecker.generateSyncStrategyIdsCacheKey(
                        userId, syncStrategy, parameters);

        Assert.assertEquals(userId, result);
    }

    @Test
    public void testReplaceAddQueryParamValue() {
        String url = "http://new-base-url/nextPage?param=value&param2=value2";
        String result = Utils.replaceAddQueryParamValue(url, "param", "replacedValue");
        Assert.assertEquals(
                "http://new-base-url/nextPage?param=replacedValue&param2=value2", result);
    }

    @Test
    public void testReplaceAddQueryParamValueNoQueryParams() {
        String url = "http://new-base-url/nextPage";
        String result = Utils.replaceAddQueryParamValue(url, "param", "replacedValue");
        Assert.assertEquals("http://new-base-url/nextPage?param=replacedValue", result);
    }

    @Test
    public void testReplaceAddQueryParamValueWithQuestionMarkNoQueryParams() {
        String url = "http://new-base-url/nextPage?";
        String result = Utils.replaceAddQueryParamValue(url, "param", "replacedValue");
        Assert.assertEquals("http://new-base-url/nextPage?param=replacedValue", result);
    }

    @Test
    public void testGetSortedInput() {
        String result = Utils.getSortedInput("gamma,zen,alpha,chi", ",");
        Assert.assertEquals("alpha,chi,gamma,zen", result);

        String result2 = Utils.getSortedInput("zebra,cat,sheep,dog", ",");
        Assert.assertEquals("cat,dog,sheep,zebra", result2);
    }
}
