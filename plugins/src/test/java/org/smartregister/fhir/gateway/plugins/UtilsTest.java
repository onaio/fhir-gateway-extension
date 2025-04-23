package org.smartregister.fhir.gateway.plugins;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.impl.GenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IGetPage;
import ca.uhn.fhir.rest.gclient.IGetPageTyped;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;

public class UtilsTest {
    private static final Logger logger = LoggerFactory.getLogger(UtilsTest.class);
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

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNullComposition() {
        Utils.getBinaryResourceReference(null, logger);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNoSection() {
        Composition composition = new Composition();
        Utils.getBinaryResourceReference(composition, logger);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithEmptySection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        composition.setSection(List.of(sectionComponent));
        Utils.getBinaryResourceReference(composition, logger);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNoMatchingSection() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference().setIdentifier(new Identifier().setValue("otherValue")));
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition, logger);
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

        composition.setSection(List.of(sectionComponent));

        String result = Utils.getBinaryResourceReference(composition, logger);
        Assert.assertEquals("Binary/1234", result);
    }

    @Test
    public void testGetBinaryResourceReferenceWithMatchingNestedSection() {
        Composition composition = new Composition();
        Composition.SectionComponent binarySectionComponent = new Composition.SectionComponent();
        Composition.SectionComponent listsSectionComponent = new Composition.SectionComponent();

        // Sets the binary
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Identifier identifier = new Identifier();
        identifier.setValue(Constants.AppConfigJsonKey.APPLICATION);
        Reference reference = new Reference();
        reference.setIdentifier(identifier);
        reference.setReference("Binary/1234");
        sectionComponent.setFocus(reference);
        binarySectionComponent.addSection(sectionComponent);

        // sets a list
        Composition.SectionComponent listSectionComponent = new Composition.SectionComponent();
        Identifier listIdentifier = new Identifier();
        listIdentifier.setValue("myList");
        Reference listReference = new Reference();
        listReference.setIdentifier(listIdentifier);
        listReference.setReference("List/1234");
        listSectionComponent.setFocus(listReference);
        listsSectionComponent.addSection(listSectionComponent);

        composition.setSection(Arrays.asList(binarySectionComponent, listsSectionComponent));

        String result = Utils.getBinaryResourceReference(composition, logger);
        Assert.assertEquals("Binary/1234", result);
    }

    @Test
    public void testGetBinaryResourceReferenceWithMatchingNestedSectionAndInvalidReference() {
        Composition composition = new Composition();
        Composition.SectionComponent binarySectionComponent = new Composition.SectionComponent();
        Composition.SectionComponent listsSectionComponent = new Composition.SectionComponent();

        // Sets the binary with an invalid reference
        Composition.SectionComponent invalidSectionComponent = new Composition.SectionComponent();
        Identifier invalidIdentifier = new Identifier();
        invalidIdentifier.setValue(Constants.AppConfigJsonKey.APPLICATION);
        binarySectionComponent.addSection(invalidSectionComponent);

        // Sets the binary
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Identifier identifier = new Identifier();
        identifier.setValue(Constants.AppConfigJsonKey.APPLICATION);
        Reference reference = new Reference();
        reference.setIdentifier(identifier);
        reference.setReference("Binary/1234");
        sectionComponent.setFocus(reference);
        binarySectionComponent.addSection(sectionComponent);

        // sets a list
        Composition.SectionComponent listSectionComponent = new Composition.SectionComponent();
        Identifier listIdentifier = new Identifier();
        listIdentifier.setValue("myList");
        Reference listReference = new Reference();
        listReference.setIdentifier(listIdentifier);
        listReference.setReference("List/1234");
        listSectionComponent.setFocus(listReference);
        listsSectionComponent.addSection(listSectionComponent);

        composition.setSection(Arrays.asList(binarySectionComponent, listsSectionComponent));

        String result = Utils.getBinaryResourceReference(composition, logger);
        Assert.assertEquals("Binary/1234", result);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNullFocus() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(null);
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition, logger);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNoIdentifier() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        Reference reference = new Reference();
        reference.setReference("Binary/5678");
        sectionComponent.setFocus(reference);
        sectionComponent.setFocus(new Reference());
        composition.setSection(Arrays.asList(sectionComponent));
        Utils.getBinaryResourceReference(composition, logger);
    }

    @Test(expected = RuntimeException.class)
    public void testGetBinaryResourceReferenceWithNoFocusReference() {
        Composition composition = new Composition();
        Composition.SectionComponent sectionComponent = new Composition.SectionComponent();
        sectionComponent.setFocus(
                new Reference()
                        .setIdentifier(
                                new Identifier().setValue(Constants.AppConfigJsonKey.APPLICATION)));
        composition.setSection(Arrays.asList(sectionComponent));
        String result = Utils.getBinaryResourceReference(composition, logger);
        Assert.assertNull(result);
    }

    @Test
    public void testFindSyncStrategyWithNullBinary() {
        String result = Utils.findSyncStrategy((Binary) null);
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
    public void testReadApplicationConfigBinaryResourceReturnsBinary() {
        Binary binary = new Binary();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("appId", "test-app");
        jsonObject.addProperty("appTitle", "Test App");

        String json = jsonObject.toString();
        String encodedJson = Base64.getEncoder().encodeToString(json.getBytes());
        binary.setDataElement(new Base64BinaryType(encodedJson));
        binary.setId("test-binary-id");

        IGenericClient client = Mockito.mock(IGenericClient.class);
        Mockito.doReturn(client)
                .when(fhirContextMock)
                .newRestfulGenericClient(ArgumentMatchers.any());

        IUntypedQuery<IBaseBundle> binaryIUntypedQuery = Mockito.mock(IUntypedQuery.class);
        Mockito.doReturn(binaryIUntypedQuery).when(client).search();

        IQuery<IBaseBundle> binaryIQuery = Mockito.mock(IQuery.class);

        Mockito.doReturn(binaryIQuery).when(binaryIUntypedQuery).forResource(Binary.class);

        IQuery<IBaseBundle> iQuery = Mockito.mock(IQuery.class);
        Mockito.doReturn(iQuery).when(binaryIQuery).where(ArgumentMatchers.any(ICriterion.class));

        Bundle bundle = new Bundle();
        bundle.getEntry().add(new Bundle.BundleEntryComponent().setResource(binary));

        Mockito.doReturn(bundle).when(iQuery).execute();

        Binary result =
                Utils.readApplicationConfigBinaryResource("test-binary-id", fhirContextMock);

        Assert.assertNotNull(result);
        Assert.assertEquals("test-binary-id", result.getId());

        byte[] binaryDataByteArray =
                Base64.getDecoder().decode(result.getDataElement().getValueAsString());
        String decodedJson = new String(binaryDataByteArray, StandardCharsets.UTF_8);

        Assert.assertEquals("{\"appId\":\"test-app\",\"appTitle\":\"Test App\"}", decodedJson);
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
    public void testExtractLogicalIdExtractsValueFromURL() {

        String url = "http://my-fhir-server:8080/fhir/Location/6969/_history/2/";
        String result = Utils.extractLogicalId(ResourceType.Location, url);

        Assert.assertEquals("6969", result);
    }

    @Test
    public void testExtractLogicalIdReturnsRawValue() {
        String result = Utils.extractLogicalId(ResourceType.Location, "7777");
        Assert.assertEquals("7777", result);
    }
}
